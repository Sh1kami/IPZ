package ua.edu.kntu.jobs;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ua.edu.kntu.jobs.Models.*;
import static org.junit.Assert.*;

/** Functional rules against actual SQLite. No in-memory repository substitute. */
public class JobPortalServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private SQLiteRepository repository;
    private JobPortalService service;
    private User seeker, employer;
    private MutableClock clock;
    @Before public void setUp() {
        repository = new SQLiteRepository(db()); clock = new MutableClock(); service = new JobPortalService(repository,new PasswordHasher(),clock);
        seeker = service.register("Навчальний шукач","seeker@example.test","TestPass26!","SEEKER");
        employer = service.register("Навчальний роботодавець","employer@example.test","TestPass26!","EMPLOYER");
    }
    @After public void tearDown() { repository.close(); }
    private Path db() { return temp.getRoot().toPath().resolve("service.sqlite"); }
    private Job createJob() { return service.createJob(employer,"Java Developer","Example Company","Київ",40000,false,"Розробка навчального Java застосунку та перевірка SQL."); }
    private Resume saveResume() { return service.saveResume(seeker,"Java Developer","Навчальний досвід програмування Java та роботи з SQL.","Java, SQL"); }
    private void error(int status, String code, Runnable action) { DomainException e=Assert.assertThrows(DomainException.class,action::run); assertEquals(status,e.status()); assertEquals(code,e.code()); }
    @Test public void normalizedEmailIsUniqueAndRejectedInsertLeavesUsersUnchanged() {
        User normalized=service.register("Інший шукач","  OTHER@EXAMPLE.TEST  ","TestPass26!","SEEKER"); assertEquals("other@example.test",normalized.email());
        error(409,"EMAIL_EXISTS",()->service.register("Дублікат"," OTHER@example.test ","TestPass26!","SEEKER"));
        assertEquals(normalized.id(),repository.userByEmail("other@example.test").orElseThrow().user().id());
    }
    @Test public void loginExpiryAtExactlyEightHoursAndLogoutInvalidateTokens() {
        Login login=service.login(" SEEKER@EXAMPLE.TEST ","TestPass26!"); assertEquals(seeker.id(),service.authenticate(login.token()).id());
        clock.advance(Duration.ofHours(8).minusMillis(1)); assertEquals(seeker.id(),service.authenticate(login.token()).id());
        clock.advance(Duration.ofMillis(1)); error(401,"UNAUTHORIZED",()->service.authenticate(login.token()));
        Login fresh=service.login("seeker@example.test","TestPass26!"); service.logout(fresh.token()); error(401,"UNAUTHORIZED",()->service.authenticate(fresh.token()));
    }
    @Test public void wrongAndUnknownCredentialsHaveSameErrorAndNoSession() {
        error(401,"INVALID_CREDENTIALS",()->service.login("seeker@example.test","Incorrect26!"));
        error(401,"INVALID_CREDENTIALS",()->service.login("missing@example.test","Incorrect26!"));
        error(401,"UNAUTHORIZED",()->service.authenticate(null));
    }
    @Test public void rolesCannotCrossResumeJobOrApplicationBoundaries() {
        error(403,"FORBIDDEN",()->service.saveResume(employer,"Title","Twenty characters required text",""));
        error(403,"FORBIDDEN",()->service.createJob(seeker,"Java Developer","Company","Київ",40000,false,"Twenty characters required text"));
        error(403,"FORBIDDEN",()->service.apply(employer,1,"")); assertTrue(repository.jobs().isEmpty()); assertTrue(repository.resume(employer.id()).isEmpty());
    }
    @Test public void resumeUpsertAndOwnUserIsolation() {
        error(404,"RESUME_NOT_FOUND",()->service.resume(seeker)); Resume first=saveResume(); assertEquals(seeker.id(),first.userId());
        User other=service.register("Інший шукач","other@example.test","TestPass26!","SEEKER"); service.saveResume(other,"Other resume","Інше навчальне резюме з потрібною довжиною.",null);
        clock.advance(Duration.ofSeconds(1)); Resume updated=service.saveResume(seeker,"Updated resume","Оновлена освіта й навчальний досвід для перевірки.",null);
        assertEquals("",updated.skills()); assertNotEquals(first.updatedAt(),updated.updatedAt()); assertEquals(updated,service.resume(seeker)); assertEquals("Other resume",service.resume(other).title());
    }
    @Test public void invalidResumeOrSalaryDoesNotReplaceValidState() {
        Resume first=saveResume(); error(400,"VALIDATION",()->service.saveResume(seeker,"abcd","Text too short","")); assertEquals(first,service.resume(seeker));
        error(400,"VALIDATION",()->service.createJob(employer,"Java Developer","Company","Київ",0,false,"Twenty characters required text")); assertTrue(repository.jobs().isEmpty());
    }
    @Test public void jobStatusOnlyChangesForOwnerAndHiddenFromDefaultCatalog() {
        Job first=createJob(); User other=service.register("Інший роботодавець","other@example.test","TestPass26!","EMPLOYER");
        error(403,"FORBIDDEN",()->service.changeJobStatus(other,first.id(),"CLOSED")); assertEquals(first,service.job(first.id()));
        Job closed=service.changeJobStatus(employer,first.id(),"CLOSED"); assertEquals(first.title(),closed.title()); assertEquals(first.salary(),closed.salary()); assertEquals(JobStatus.CLOSED,closed.status());
        assertTrue(service.jobs(new JobFilter(null,null,0,null,null)).isEmpty()); assertEquals(List.of(closed),service.jobs(new JobFilter(null,null,0,null,"ALL")));
    }
    @Test public void applicationPreconditionsDoNotInsertRows() {
        Job job=createJob(); error(409,"RESUME_REQUIRED",()->service.apply(seeker,job.id(),"")); assertTrue(service.applications(seeker).isEmpty());
        saveResume(); service.changeJobStatus(employer,job.id(),"CLOSED"); error(409,"JOB_CLOSED",()->service.apply(seeker,job.id(),""));
        error(404,"JOB_NOT_FOUND",()->service.apply(seeker,999999,"")); assertTrue(service.applications(seeker).isEmpty());
    }
    @Test public void applicationSnapshotDuplicateAndPrivacyArePersisted() {
        Job job=createJob(); Resume resume=saveResume(); Application app=service.apply(seeker,job.id(),"Навчальний відгук.");
        assertEquals(resume.title(),app.resumeTitle()); assertEquals(resume.text(),app.resumeText()); assertEquals(resume.skills(),app.resumeSkills());
        service.saveResume(seeker,"Updated resume","Пізніше змінене резюме не повинно змінювати відгук.","Python");
        error(409,"DUPLICATE_APPLICATION",()->service.apply(seeker,job.id(),"Повтор")); assertEquals(List.of(app),service.applications(seeker));
        User other=service.register("Інший шукач","other@example.test","TestPass26!","SEEKER"); assertTrue(service.applications(other).isEmpty());
    }
    @Test public void simultaneousDuplicateApplicationCreatesExactlyOneRow() throws Exception {
        Job job=createJob(); saveResume(); ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        Callable<Integer> submit=()->{start.await();try{service.apply(seeker,job.id(),"");return 201;}catch(DomainException e){return e.status();}};
        try { Future<Integer> one=pool.submit(submit),two=pool.submit(submit);start.countDown(); var statuses=List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)).stream().sorted().toList(); assertEquals(List.of(201,409),statuses);assertEquals(1,service.applications(seeker).size()); }
        finally { pool.shutdownNow(); }
    }
    @Test public void reopeningSameDatabasePreservesAllEntitiesAndOtherFileIsEmpty() {
        Job job=createJob(); saveResume(); Application app=service.apply(seeker,job.id(),""); repository.close();
        try (SQLiteRepository reopened=new SQLiteRepository(db());SQLiteRepository independent=new SQLiteRepository(temp.getRoot().toPath().resolve("independent.sqlite"))) {
            assertEquals(seeker,reopened.userByEmail("seeker@example.test").orElseThrow().user());assertEquals(job,reopened.job(job.id()).orElseThrow());assertEquals(List.of(app),reopened.applications(seeker.id()));assertTrue(reopened.resume(seeker.id()).isPresent());assertTrue(independent.jobs().isEmpty());
        }
    }
    @Test public void fourVacancySeedIsIdempotentAndFiltersHaveIndependentOracle() {
        service.seedDemo(); service.seedDemo(); assertEquals(4,repository.jobs().size());
        List<Job> result=service.jobs(new JobFilter("java","київ",30000,true,"ACTIVE")); assertEquals(1,result.size());assertEquals("Junior Java Developer",result.get(0).title());
        assertEquals(List.of("Java Developer"),service.jobs(new JobFilter("java","КИЇВ",40000,false,"ACTIVE")).stream().map(Job::title).toList());
        assertTrue(service.jobs(new JobFilter("java","Київ",40001,false,"ACTIVE")).isEmpty());
        assertEquals("Java Developer Closed",service.jobs(new JobFilter("java","Київ",60000,false,"CLOSED")).get(0).title());
    }
    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-10-13T12:00:00Z");
        void advance(Duration duration){now=now.plus(duration);} @Override public ZoneId getZone(){return ZoneOffset.UTC;} @Override public Clock withZone(ZoneId zone){return this;} @Override public Instant instant(){return now;}
    }
}
