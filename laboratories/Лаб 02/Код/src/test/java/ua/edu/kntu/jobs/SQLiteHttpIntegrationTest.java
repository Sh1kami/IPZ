package ua.edu.kntu.jobs;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Independent real HTTP clients call the actual service and SQLite JDBC repository. */
public class SQLiteHttpIntegrationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private JobPortalServer portal;
    private HttpClient client;
    private Path db;
    private final Gson gson=new Gson();
    @Before public void before() throws Exception {db=temp.getRoot().toPath().resolve("http.sqlite");portal=new JobPortalServer(db).start(0);client=HttpClient.newHttpClient();}
    @After public void after(){portal.close();}
    private record Response(int status,JsonObject body,HttpResponse<String> raw) {}
    private Response send(String method,String path,Object body,String cookie) throws Exception {
        return raw(method,path,body==null?null:gson.toJson(body),cookie,body==null?null:"application/json",null);
    }
    private Response raw(String method,String path,String body,String cookie,String type,String origin) throws Exception {
        var b=HttpRequest.newBuilder(URI.create(portal.baseUrl()+path));
        if(cookie!=null)b.header("Cookie",cookie);if(type!=null)b.header("Content-Type",type);if(origin!=null)b.header("Origin",origin);
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
        var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));return new Response(r.statusCode(),JsonParser.parseString(r.body()).getAsJsonObject(),r);
    }
    private long register(String email,String role) throws Exception {Response r=send("POST","/api/auth/register",Map.of("name","Навчальний користувач","email",email,"password","TestPass26!","role",role),null);assertEquals(201,r.status());return r.body().getAsJsonObject("user").get("id").getAsLong();}
    private String login(String email) throws Exception {Response r=send("POST","/api/auth/login",Map.of("email",email,"password","TestPass26!"),null);assertEquals(200,r.status());return r.raw().headers().firstValue("Set-Cookie").orElseThrow().split(";",2)[0];}
    private String account(String email,String role) throws Exception {register(email,role);return login(email);}
    private Map<String,Object> jobBody(){return Map.of("title","Java Developer","company","Example Company","city","Київ","salary",40000,"remote",false,"description","Розробка Java навчального застосунку та перевірка SQL.");}
    private long createJob(String cookie) throws Exception {Response r=send("POST","/api/jobs",jobBody(),cookie);assertEquals(201,r.status());long id=r.body().getAsJsonObject("job").get("id").getAsLong();assertEquals("/api/jobs/"+id,r.raw().headers().firstValue("Location").orElseThrow());return id;}
    private void resume(String cookie) throws Exception {assertEquals(200,send("PUT","/api/me/resume",Map.of("title","Java Developer","text","Навчальний досвід програмування Java та роботи з SQL.","skills","Java, SQL"),cookie).status());}
    private void error(Response r,int status,String code){assertEquals(r.raw().body(),status,r.status());assertEquals(code,r.body().getAsJsonObject("error").get("code").getAsString());}
    private String enc(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}

    @Test public void registrationNormalizesDuplicateAndDoesNotAuthenticate() throws Exception {
        register(" SEEKER@EXAMPLE.TEST ","SEEKER");Response duplicate=send("POST","/api/auth/register",Map.of("name","Інший користувач","email","seeker@example.test","password","OtherPass26!","role","SEEKER"),null);error(duplicate,409,"EMAIL_EXISTS");
        error(send("GET","/api/me",null,null),401,"UNAUTHORIZED");String cookie=login("seeker@example.test");Response me=send("GET","/api/me",null,cookie);assertEquals("seeker@example.test",me.body().getAsJsonObject("user").get("email").getAsString());assertFalse(me.raw().body().contains("password"));assertFalse(me.raw().body().contains("hash"));
    }
    @Test public void actualLoginCookieDigestLogoutAndGenericWrongCredentials() throws Exception {
        register("seeker@example.test","SEEKER");Response login=send("POST","/api/auth/login",Map.of("email","seeker@example.test","password","TestPass26!"),null);
        String header=login.raw().headers().firstValue("Set-Cookie").orElseThrow();assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Strict"));assertTrue(header.contains("Path=/"));String cookie=header.split(";",2)[0],token=cookie.substring("JSESSION=".length());
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery("SELECT token_hash FROM sessions")){assertTrue(r.next());assertEquals(PasswordHasher.tokenDigest(token),r.getString(1));assertNotEquals(token,r.getString(1));}
        Response wrong=send("POST","/api/auth/login",Map.of("email","seeker@example.test","password","WrongPass26!"),null),missing=send("POST","/api/auth/login",Map.of("email","missing@example.test","password","WrongPass26!"),null);error(wrong,401,"INVALID_CREDENTIALS");error(missing,401,"INVALID_CREDENTIALS");assertEquals(wrong.body(),missing.body());
        assertEquals(200,send("POST","/api/auth/logout",null,cookie).status());error(send("GET","/api/me",null,cookie),401,"UNAUTHORIZED");
    }
    @Test public void resumeUpsertIsOnlyForOwnSeekerAndDefaultSkillsIsEmpty() throws Exception {
        String one=account("one@example.test","SEEKER"),two=account("two@example.test","SEEKER");error(send("GET","/api/me/resume",null,one),404,"RESUME_NOT_FOUND");resume(one);resume(two);
        assertEquals(200,send("PUT","/api/me/resume",Map.of("title","Updated resume","text","Оновлений навчальний досвід і освіта для пошуку роботи."),one).status());
        Response saved=send("GET","/api/me/resume",null,one);assertEquals("Updated resume",saved.body().getAsJsonObject("resume").get("title").getAsString());assertEquals("",saved.body().getAsJsonObject("resume").get("skills").getAsString());assertEquals("Java Developer",send("GET","/api/me/resume",null,two).body().getAsJsonObject("resume").get("title").getAsString());
    }
    @Test public void ownershipAndRoleMatrixPreserveDatabase() throws Exception {
        String employer=account("employer@example.test","EMPLOYER"),other=account("other@example.test","EMPLOYER"),seeker=account("seeker@example.test","SEEKER");long id=createJob(employer);
        error(send("POST","/api/jobs",jobBody(),null),401,"UNAUTHORIZED");error(send("POST","/api/jobs",jobBody(),seeker),403,"FORBIDDEN");error(send("PUT","/api/me/resume",Map.of("title","Valid title","text","This text has at least twenty characters."),employer),403,"FORBIDDEN");error(send("POST","/api/jobs/"+id+"/applications",Map.of(),employer),403,"FORBIDDEN");
        error(send("PATCH","/api/jobs/"+id,Map.of("status","CLOSED"),other),403,"FORBIDDEN");assertEquals("ACTIVE",send("GET","/api/jobs/"+id,null,null).body().getAsJsonObject("job").get("status").getAsString());assertEquals(1,send("GET","/api/jobs",null,null).body().get("count").getAsInt());
    }
    @Test public void createdJobDetailsAndStatusChangePreserveFieldsAndIgnoreSpoofedOwner() throws Exception {
        String employer=account("employer@example.test","EMPLOYER");long id=createJob(employer);Response before=send("GET","/api/jobs/"+id,null,null);
        Response after=send("PATCH","/api/jobs/"+id,Map.of("status","CLOSED","title","Spoof title","ownerId",999),employer);assertEquals(200,after.status());JsonObject job=after.body().getAsJsonObject("job");assertEquals("CLOSED",job.get("status").getAsString());job.addProperty("status","ACTIVE");assertEquals(before.body().getAsJsonObject("job"),job);
        assertEquals(0,send("GET","/api/jobs",null,null).body().get("count").getAsInt());assertEquals(1,send("GET","/api/jobs?status=CLOSED",null,null).body().get("count").getAsInt());
    }
    @Test public void unicodeSearchAndCombinedFiltersHaveExactSeedOracle() throws Exception {
        portal.service().seedDemo();Response all=send("GET","/api/jobs?status=ALL",null,null);assertEquals(4,all.body().get("count").getAsInt());JsonArray jobs=all.body().getAsJsonArray("jobs");assertTrue(jobs.get(0).getAsJsonObject().get("id").getAsLong()<jobs.get(3).getAsJsonObject().get("id").getAsLong());
        Response result=send("GET","/api/jobs?query=JAVA&city="+enc("КИЇВ")+"&minSalary=30000&remote=true",null,null);assertEquals(1,result.body().get("count").getAsInt());assertEquals("Junior Java Developer",result.body().getAsJsonArray("jobs").get(0).getAsJsonObject().get("title").getAsString());
        assertEquals(1,send("GET","/api/jobs?query=java&city="+enc("Київ")+"&minSalary=40000&remote=false",null,null).body().get("count").getAsInt());assertEquals(0,send("GET","/api/jobs?query=java&city="+enc("Київ")+"&minSalary=40001&remote=false",null,null).body().get("count").getAsInt());assertEquals(0,send("GET","/api/jobs?query="+enc("%_"),null,null).body().get("count").getAsInt());assertEquals(0,send("GET","/api/jobs?query="+enc("' OR '1'='1"),null,null).body().get("count").getAsInt());
    }
    @Test public void validApplicationSnapshotDuplicateAndPrivateList() throws Exception {
        String employer=account("employer@example.test","EMPLOYER"),seeker=account("seeker@example.test","SEEKER"),other=account("other@example.test","SEEKER");long id=createJob(employer);resume(seeker);
        Response first=send("POST","/api/jobs/"+id+"/applications",Map.of("coverLetter","Навчальний супровідний лист."),seeker);assertEquals(201,first.status());assertEquals("Java Developer",first.body().getAsJsonObject("application").get("resumeTitle").getAsString());
        assertEquals(200,send("PUT","/api/me/resume",Map.of("title","Changed resume","text","Змінений навчальний досвід після подачі першого відгуку."),seeker).status());error(send("POST","/api/jobs/"+id+"/applications",Map.of(),seeker),409,"DUPLICATE_APPLICATION");
        Response list=send("GET","/api/me/applications",null,seeker);assertEquals(1,list.body().get("count").getAsInt());assertEquals(first.body().getAsJsonObject("application"),list.body().getAsJsonArray("applications").get(0));assertEquals(0,send("GET","/api/me/applications",null,other).body().get("count").getAsInt());
    }
    @Test public void applicationPreconditionsAndInvalidLetterProduceNoRows() throws Exception {
        String employer=account("employer@example.test","EMPLOYER"),seeker=account("seeker@example.test","SEEKER");long id=createJob(employer);error(send("POST","/api/jobs/"+id+"/applications",Map.of(),seeker),409,"RESUME_REQUIRED");resume(seeker);
        error(send("POST","/api/jobs/"+id+"/applications",Map.of("coverLetter","x".repeat(2001)),seeker),400,"VALIDATION");send("PATCH","/api/jobs/"+id,Map.of("status","CLOSED"),employer);error(send("POST","/api/jobs/"+id+"/applications",Map.of(),seeker),409,"JOB_CLOSED");error(send("POST","/api/jobs/999999/applications",Map.of(),seeker),404,"JOB_NOT_FOUND");assertEquals(0,send("GET","/api/me/applications",null,seeker).body().get("count").getAsInt());
    }
    @Test public void strictJsonTypesBodyLimitAndMediaTypeAreRejectedWithoutInsert() throws Exception {
        String employer=account("employer@example.test","EMPLOYER");var invalid=new HashMap<String,Object>(jobBody());invalid.put("salary","40000");error(send("POST","/api/jobs",invalid,employer),400,"VALIDATION");invalid=new HashMap<>(jobBody());invalid.put("remote","false");error(send("POST","/api/jobs",invalid,employer),400,"VALIDATION");invalid=new HashMap<>(jobBody());invalid.put("salary",40000.5);error(send("POST","/api/jobs",invalid,employer),400,"VALIDATION");
        error(raw("POST","/api/jobs","{broken",employer,"application/json",null),400,"BAD_JSON");error(raw("POST","/api/jobs","{} {}",employer,"application/json",null),400,"BAD_JSON");error(raw("POST","/api/jobs","{}",employer,"text/plain",null),415,"UNSUPPORTED_MEDIA_TYPE");error(raw("POST","/api/jobs","x".repeat(65537),employer,"application/json",null),413,"BODY_TOO_LARGE");assertEquals(0,send("GET","/api/jobs",null,null).body().get("count").getAsInt());
    }
    @Test public void routesMethodsIdsAndMalformedFiltersHaveStableErrors() throws Exception {
        for(String id:List.of("0","-1","abc","9223372036854775808"))error(send("GET","/api/jobs/"+id,null,null),400,"VALIDATION");error(send("GET","/api/jobs/999999",null,null),404,"JOB_NOT_FOUND");error(send("GET","/api/unknown",null,null),404,"NOT_FOUND");error(send("DELETE","/api/jobs",null,null),405,"METHOD_NOT_ALLOWED");
        for(String params:List.of("minSalary=-1","minSalary=1.5","minSalary=1000001","remote=TRUE","status=active","city=x&city=y","unknown=x"))error(send("GET","/api/jobs?"+params,null,null),400,"VALIDATION");
    }
    @Test public void originUsesActualDynamicPortAndHeadersArePresent() throws Exception {
        var data=Map.of("name","Навчальний користувач","email","one@example.test","password","TestPass26!","role","SEEKER");
        Response foreign=raw("POST","/api/auth/register",gson.toJson(data),null,"application/json","http://other.example.test");error(foreign,403,"ORIGIN_NOT_ALLOWED");Response same=raw("POST","/api/auth/register",gson.toJson(data),null,"application/json",portal.baseUrl());assertEquals(201,same.status());
        assertEquals("nosniff",same.raw().headers().firstValue("X-Content-Type-Options").orElseThrow());assertTrue(same.raw().headers().firstValue("Content-Security-Policy").orElseThrow().contains("script-src 'self'"));assertEquals("no-store",same.raw().headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test public void parallelHttpDuplicateApplicationYieldsOneCreatedAndOneConflict() throws Exception {
        String employer=account("employer@example.test","EMPLOYER"),seeker=account("seeker@example.test","SEEKER");long id=createJob(employer);resume(seeker);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Integer> submit=()->{gate.await();return send("POST","/api/jobs/"+id+"/applications",Map.of(),seeker).status();};
        try{var one=pool.submit(submit);var two=pool.submit(submit);gate.countDown();assertEquals(List.of(201,409),List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)).stream().sorted().toList());assertEquals(1,send("GET","/api/me/applications",null,seeker).body().get("count").getAsInt());}finally{pool.shutdownNow();}
    }
    @Test public void restartingHttpWithSameDatabasePreservesSessionAndEntities() throws Exception {
        String employer=account("employer@example.test","EMPLOYER"),seeker=account("seeker@example.test","SEEKER");long id=createJob(employer);resume(seeker);assertEquals(201,send("POST","/api/jobs/"+id+"/applications",Map.of(),seeker).status());portal.close();portal=new JobPortalServer(db).start(0);
        assertEquals(200,send("GET","/api/me",null,seeker).status());assertEquals("Java Developer",send("GET","/api/jobs/"+id,null,null).body().getAsJsonObject("job").get("title").getAsString());assertEquals(200,send("GET","/api/me/resume",null,seeker).status());assertEquals(1,send("GET","/api/me/applications",null,seeker).body().get("count").getAsInt());
    }
}
