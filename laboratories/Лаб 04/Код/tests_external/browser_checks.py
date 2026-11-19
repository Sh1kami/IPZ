"""Selenium UI and stored-XSS checks. Run against the local Docker fixture."""
from pathlib import Path
import os,json,time,uuid
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait,Select
from selenium.webdriver.support import expected_conditions as EC
ROOT=Path(__file__).resolve().parents[1];E=ROOT/'evidence/browser';E.mkdir(parents=True,exist_ok=True)
URL=os.getenv('IPZ_BROWSER_URL','http://panchyshkin-ipz-app:18727')
def run():
    options=webdriver.ChromeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1440,1100')
    driver=webdriver.Remote(command_executor=os.getenv('SELENIUM_URL','http://127.0.0.1:4447/wd/hub'),options=options)
    wait=WebDriverWait(driver,20);results=[]
    def el(id):return driver.find_element(By.ID,id)
    def fill(id,value):el(id).clear();el(id).send_keys(value)
    def text(id,value):wait.until(lambda d:value in el(id).text)
    def passed(id,title):results.append({'id':id,'name':title,'status':'PASS'});print(id,'PASS',flush=True)
    def click(id):
        element=el(id);driver.execute_script('arguments[0].scrollIntoView({block:"center"})',element);wait.until(EC.element_to_be_clickable((By.ID,id))).click()
    try:
        driver.get(URL);text('job-count','3');assert driver.title=='Навчальний сервіс пошуку роботи'
        driver.save_screenshot(str(E/'01-catalog.png'));passed('UI01','Load initial catalog')
        fill('search-query','JAVA');click('search-submit');text('job-count','2');passed('UI02','Case insensitive search')
        fill('search-city','Київ');fill('search-minsalary','40000');Select(el('search-remote')).select_by_value('false');click('search-submit');text('job-count','1')
        assert driver.find_element(By.CSS_SELECTOR,'.job-card h3').text=='Java Developer'
        driver.save_screenshot(str(E/'02-filter.png'));passed('UI03','Combined filters and salary boundary')
        driver.find_element(By.CSS_SELECTOR,'[data-testid="job-details"]').click();wait.until(EC.visibility_of_element_located((By.ID,'job-dialog')))
        assert el('detail-title').text=='Java Developer';click('detail-close');passed('UI04','Open and close details')
        click('search-reset');text('job-count','3');passed('UI05','Reset filters')
        account='ui-'+uuid.uuid4().hex[:8]+'@example.test'
        fill('register-name','Богдан Панчишкін');fill('register-email',account);fill('register-password','TestPass26!');click('register-submit');text('message','Обліковий запис створено')
        passed('UI06','Register seeker through form')
        fill('login-password','TestPass26!');click('login-submit');text('current-user',account);passed('UI07','Login and role-specific panel')
        fill('resume-title','Junior QA Engineer');fill('resume-text','Досвід Java, SQL та автоматизованого тестування програм.');fill('resume-skills','JUnit, Selenium');click('resume-submit');text('message','Резюме збережено')
        driver.save_screenshot(str(E/'03-resume.png'));passed('UI08','Save resume')
        button=driver.find_element(By.CSS_SELECTOR,'[data-testid="job-apply"]');driver.execute_script('arguments[0].scrollIntoView({block:"center"})',button);button.click()
        wait.until(EC.visibility_of_element_located((By.ID,'apply-dialog')));fill('apply-letter','Готовий до співбесіди.');click('apply-confirm');text('message','Відгук надіслано')
        assert len(driver.find_elements(By.CSS_SELECTOR,'.application-card'))==1
        driver.save_screenshot(str(E/'04-application.png'));passed('UI09','Apply with resume')
        click('logout-button');text('message','Ви вийшли');passed('UI10','Logout')
        # Stored-XSS fixture is inserted through the actual REST endpoint.
        from api_checks import Client
        client=Client();suffix=uuid.uuid4().hex[:8]
        owner={'name':'Test employer','email':f'xss-{suffix}@example.test','password':'TestPass26!','role':'EMPLOYER'}
        assert client.request('POST','/api/auth/register',owner)[0]==201
        assert client.request('POST','/api/auth/login',{'email':owner['email'],'password':owner['password']})[0]==200
        payload='<img src=x onerror="window.__ipz_xss=1">'
        vacancy={'title':payload,'company':'IPZ Security '+suffix,'city':'Київ','salary':40000,'remote':True,'description':'Навчальний текст для перевірки збереженого XSS.'}
        assert client.request('POST','/api/jobs',vacancy)[0]==201
        fill('search-query','IPZ Security '+suffix);click('search-submit');text('job-count','1')
        assert driver.find_element(By.CSS_SELECTOR,'.job-card h3').text==payload
        assert not driver.find_elements(By.CSS_SELECTOR,'.job-card img')
        assert driver.execute_script('return window.__ipz_xss || null') is None
        driver.save_screenshot(str(E/'05-xss-literal.png'));passed('SEC07','Stored XSS rendered as text')
        (E/'capabilities.json').write_text(json.dumps(driver.capabilities,ensure_ascii=False,indent=2),encoding='utf-8')
    finally:
        (E/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');driver.quit()
    print(len(results),'browser checks PASS',flush=True)
if __name__=='__main__':run()
