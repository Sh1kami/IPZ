package ua.edu.kntu.jobs;
import org.junit.runner.RunWith;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
@RunWith(Cucumber.class)
@CucumberOptions(features="src/test/resources/features", glue="ua.edu.kntu.jobs.bdd",
    plugin={"pretty", "json:target/cucumber.json", "html:target/cucumber.html"})
public class RunCucumberTest { }
