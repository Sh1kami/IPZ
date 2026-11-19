package ua.edu.kntu.jobs.bdd;
import io.cucumber.java.*;
import io.cucumber.java.en.*;
import java.nio.file.*;
import java.util.List;
import ua.edu.kntu.jobs.*;
import ua.edu.kntu.jobs.Models.*;
import static org.junit.Assert.*;
public class JobSteps {
    private Path dir;
    private SQLiteRepository repository;
    private JobPortalService service;
    private User seeker, employer;
    private Job vacancy;
    private List<Job> found;
    private int error;
    @Before public void setup() throws Exception {
        dir=Files.createTempDirectory("ipz-bdd-");
        repository=new SQLiteRepository(dir.resolve("bdd.sqlite"));
        service=new JobPortalService(repository);
    }
    @After public void cleanup() throws Exception {
        repository.close();
        try(var paths=Files.walk(dir)) {
            for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    @Given("the catalog contains the demo vacancies")
    public void catalog(){service.seedDemo();}
    @When("I search for {string} in {string} with minimum salary {int}")
    public void search(String query,String city,int salary){found=service.jobs(new JobFilter(query,city,salary,null,"ACTIVE"));}
    @Then("I receive {int} vacancies")
    public void count(int expected){assertEquals(expected,found.size());}
    @Given("a registered seeker with a saved resume")
    public void seeker(){
        seeker=service.register("Богдан Панчишкін","bohdan@example.test","SecurePass26!","SEEKER");
        employer=service.register("Роботодавець","work@example.test","SecurePass26!","EMPLOYER");
        service.saveResume(seeker,"Java Developer","Досвід Java та SQL для навчального проєкту.","Java, SQL");
    }
    @Given("an active vacancy") public void active(){
        vacancy=service.createJob(employer,"Java Developer","IPZ Company","Київ",40000,true,"Розробка програмного забезпечення та автоматизація тестів.");
    }
    @Given("a closed vacancy") public void closed(){active();service.changeJobStatus(employer,vacancy.id(),"CLOSED");}
    private void apply(){try{service.apply(seeker,vacancy.id(),"Готовий до співбесіди.");}catch(DomainException e){error=e.status();}}
    @When("the seeker applies once") public void once(){apply();}
    @When("the seeker applies twice") public void twice(){apply();assertEquals(0,error);apply();}
    @Then("there is exactly {int} application") public void applications(int expected){assertEquals(expected,service.applications(seeker).size());}
    @Then("the second application is rejected with status {int}") public void secondError(int expected){assertEquals(expected,error);}
    @Then("the application is rejected with status {int}") public void firstError(int expected){assertEquals(expected,error);}
}
