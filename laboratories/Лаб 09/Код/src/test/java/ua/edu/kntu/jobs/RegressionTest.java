package ua.edu.kntu.jobs;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.List;
import ua.edu.kntu.jobs.Models.*;
public class RegressionTest {
    private Job job;
    @Before public void setup(){job=new Job(1,1,"Java Developer","IPZ Company","Київ",40000,true,"Опис Java розробника для тестування.",JobStatus.ACTIVE,"2026-11-19T00:00:00Z");}
    @Test public void salaryLowerBoundary(){assertTrue(new JobFilter("java",null,40000,null,null).matches(job));}
    @Test public void salaryAboveBoundary(){assertFalse(new JobFilter("java",null,40001,null,null).matches(job));}
    @Test public void caseInsensitiveSearch(){assertTrue(new JobFilter("JAVA",null,0,null,null).matches(job));}
    @Test public void cityCaseInsensitive(){assertTrue(new JobFilter(null,"КИЇВ",0,null,null).matches(job));}
    @Test public void combinedFilters(){assertTrue(new JobFilter("java","Київ",40000,true,"ACTIVE").matches(job));}
    @Test public void remoteMismatch(){assertFalse(new JobFilter("java",null,0,false,null).matches(job));}
}
