Feature: Job search and application
  A seeker finds vacancies and sends one application with a saved resume.

  Scenario: Case insensitive search
    Given the catalog contains the demo vacancies
    When I search for "JAVA" in "Київ" with minimum salary 30000
    Then I receive 2 vacancies

  Scenario: No matching vacancy
    Given the catalog contains the demo vacancies
    When I search for "Rust" in "Київ" with minimum salary 0
    Then I receive 0 vacancies

  Scenario: Salary boundary is inclusive
    Given the catalog contains the demo vacancies
    When I search for "Java" in "Київ" with minimum salary 40000
    Then I receive 1 vacancies

  Scenario: Successful application
    Given a registered seeker with a saved resume
    And an active vacancy
    When the seeker applies once
    Then there is exactly 1 application

  Scenario: Repeated application is rejected
    Given a registered seeker with a saved resume
    And an active vacancy
    When the seeker applies twice
    Then the second application is rejected with status 409
    And there is exactly 1 application

  Scenario: Closed vacancy is unavailable
    Given a registered seeker with a saved resume
    And a closed vacancy
    When the seeker applies once
    Then the application is rejected with status 409
    And there is exactly 0 application
