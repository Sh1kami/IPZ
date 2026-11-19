# ІПЗ. Якість і тестування програмного забезпечення

**Панчишкін Богдан Олександрович · група 3ПРс/3**

Лабораторні роботи 1–12: звіти, програмний код, автоматизовані перевірки та результати запусків. Об’єкт тестування — сервіс пошуку роботи з Java HTTP API, SQLite і браузерним інтерфейсом. Для мобільної роботи створено Android-каталог вакансій.

## Роботи

Для Moodle у кожній папці підготовлено **Звіт.pdf** та **Код проекта.pdf** з клікабельним посиланням на код. Каталог **Код** відкривається у GitHub без розпакування архівів.

| № | Тема | Звіт | Код і матеріали |
| --- | --- | --- | --- |
| 1 | Вступ до тестування | [PDF](laboratories/Лаб%2001/Звіт.pdf) | [Код](laboratories/Лаб%2001/Код) |
| 2 | Функціональне тестування | [PDF](laboratories/Лаб%2002/Звіт.pdf) | [Код](laboratories/Лаб%2002/Код) |
| 3 | Нефункціональне тестування, JMeter | [PDF](laboratories/Лаб%2003/Звіт.pdf) | [Код](laboratories/Лаб%2003/Код) |
| 4 | Автоматизація, Selenium | [PDF](laboratories/Лаб%2004/Звіт.pdf) | [Код](laboratories/Лаб%2004/Код) |
| 5 | Юніт-тести JUnit | [PDF](laboratories/Лаб%2005/Звіт.pdf) | [Код](laboratories/Лаб%2005/Код) |
| 6 | Інтеграційне тестування | [PDF](laboratories/Лаб%2006/Звіт.pdf) | [Код](laboratories/Лаб%2006/Код) |
| 7 | REST API, Postman і Newman | [PDF](laboratories/Лаб%2007/Звіт.pdf) | [Код](laboratories/Лаб%2007/Код) |
| 8 | SQL injection, XSS і захист доступу | [PDF](laboratories/Лаб%2008/Звіт.pdf) | [Код](laboratories/Лаб%2008/Код) |
| 9 | Android, Appium і UiAutomator2 | [PDF](laboratories/Лаб%2009/Звіт.pdf) | [Код](laboratories/Лаб%2009/Код) |
| 10 | Регресійне тестування | [PDF](laboratories/Лаб%2010/Звіт.pdf) | [Код](laboratories/Лаб%2010/Код) |
| 11 | BDD, Gherkin і Cucumber | [PDF](laboratories/Лаб%2011/Звіт.pdf) | [Код](laboratories/Лаб%2011/Код) |
| 12 | Метрики й покриття JaCoCo | [PDF](laboratories/Лаб%2012/Звіт.pdf) | [Код](laboratories/Лаб%2012/Код) |

## Результати

- 67 успішних Java-виконань: Startup 1, Validation 11, JobFilter 11, PasswordHasher 7, Service/SQLite 12, HTTP/SQLite 13, Regression 6, Cucumber 6.
- 10 основних Selenium-сценаріїв та браузерна перевірка stored XSS.
- 26 функціональних API-кейсів і 6 додаткових HTTP-перевірок безпеки.
- Postman/Newman: 12 запитів, 27 assertions, 0 failures.
- Appium: 12 успішних перевірок на Android 9 у телефонній і планшетній конфігураціях.
- JMeter: 1260 запитів, 0 помилок, p95 52–55 мс у дослідженому локальному навантаженні.
- JaCoCo: 91,02% рядків, 79,77% гілок, 89,15% інструкцій у розширеному Java-наборі.

Регресійний набір виявив контрольну заміну `>=` на `>`: 2 з 6 перевірок впали. Після виправлення всі шість пройшли. Деталі — [REG01](test-docs/REG01.md).

## Структура

- `src/main` — застосунок і браузерний інтерфейс.
- `src/test` — JUnit, Cucumber та Gherkin-сценарії.
- `tests_external` — Selenium, HTTP API та Appium.
- `postman` — колекція Postman.
- `load` — JMeter-план.
- `mobile` — Android-застосунок.
- `test-docs` — вимоги, кейси, таблиці рішень і результати.
- `evidence` — збережені результати, JaCoCo, JMeter і скриншоти.
- `laboratories` — комплекти всіх робіт для перегляду й здачі.

## Java та модульні тести

Потрібні JDK 17+ і Maven 3.9+:

```bash
mvn -B -ntp clean verify
java -jar target/job-portal-ipz-1.0.0.jar --port 18727 --db data/jobs.sqlite --seed
```

Адреса локального запуску: <http://127.0.0.1:18727>. Окремі набори:

```bash
mvn -B -ntp -Dtest=StartupTest verify
mvn -B -ntp -Dtest=ValidationTest,JobFilterTest,PasswordHasherTest verify
mvn -B -ntp -Dtest=JobPortalServiceTest,SQLiteHttpIntegrationTest verify
mvn -B -ntp -Dtest=RegressionTest test
mvn -B -ntp -Dtest=RunCucumberTest verify
```

Нові результати: `target/surefire-reports`, `target/site/jacoco/index.html`, `target/cucumber.html`. Збережені результати виконаних робіт — в `evidence/full` і `evidence/baseline`.

## Браузер та API

Docker Compose запускає застосунок і Selenium Chromium. Команди нижче виконуються з кореня репозиторію:

```bash
docker compose up --build -d
python -m pip install -r requirements-tests.txt
python tests_external/browser_checks.py
python tests_external/api_checks.py
```

Перед першим браузерним прогоном потрібен початковий каталог із трьох відкритих вакансій. Браузерний сценарій виконуйте перед API-сценаріями: останні додають дані. Для нового незалежного циклу використайте новий том або приберіть навчальні дані цього Compose-проєкту командою `docker compose down --volumes`.

Для Postman можна імпортувати `postman/IPZ.postman_collection.json`. Запуск тієї ж колекції в Docker:

```bash
docker run --rm --network ipz-labs-net -v "${PWD}:/workspace" postman/newman:alpine run /workspace/postman/IPZ.postman_collection.json
```

Змінні `IPZ_BASE_URL`, `IPZ_BROWSER_URL` та `SELENIUM_URL` дозволяють перевизначити адреси. Типові значення відповідають Compose-конфігурації.

## JMeter

```bash
docker run --rm --network ipz-labs-net -v "${PWD}:/workspace" justb4/jmeter:5.5 -n -t /workspace/load/catalog.jmx -Jusers=15 -Jloops=20 -l /workspace/results-15.jtl
```

Для інших прогонів задайте `-Jusers=1` або `-Jusers=5`. Збережені JTL, Dashboard і підсумок містяться в `evidence/load`. Локальний критерій: p95 ≤ 500 мс і відсутність HTTP-помилок.

## Android та Appium

Порядок побудови середовища:

```bash
docker build -f tools/android-base.Dockerfile -t panchyshkin-ipz-android-base:lab09 .
docker run --rm -v "${PWD}:/workspace" -w /workspace/mobile panchyshkin-ipz-android-base:lab09 gradle assembleDebug
docker build -f tools/mobile-emulator.Dockerfile -t panchyshkin-ipz-emulator:lab09 .
docker build -f tools/appium.Dockerfile -t panchyshkin-ipz-appium:lab09 .
docker build -f tools/emulator-runtime.Dockerfile -t panchyshkin-ipz-mobile:lab09 .
docker run -d --name panchyshkin-ipz-mobile -p 127.0.0.1:4729:4723 --shm-size=2g -v "${PWD}:/workspace" panchyshkin-ipz-mobile:lab09
python tests_external/mobile_checks.py
```

Профілі: Android 9 / API 28, 480×800 та 800×1280, density 160. Сценарій очікує завершення завантаження. У програмній емуляції старт може тривати кілька хвилин. Зібраний debug APK міститься в комплекті лабораторної 9.

## Контракт і навчальні дані

Правила API визначені в [API-contract.json](API-contract.json), а зв’язок вимог із тестами — у [test-docs](test-docs). `--seed` створює чотири навчальні вакансії: три ACTIVE та одну CLOSED. Для автоматизованих перевірок використовуються лише навчальні адреси `example.test` й окремі бази SQLite.
