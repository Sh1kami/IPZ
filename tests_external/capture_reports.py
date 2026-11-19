from pathlib import Path
import os,json
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
ROOT=Path(__file__).resolve().parents[1];E=ROOT/'evidence/screenshots';E.mkdir(exist_ok=True)
def run():
    import html
    for name in ['lab10-detected-regression','lab10-fixed-regression']:
        source=(ROOT/'evidence'/(name+'.log')).read_text(encoding='utf-8')
        view='<!doctype html><meta charset="utf-8"><style>body{font:20px monospace;margin:20px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style><pre>'+html.escape(source)+'</pre>'
        (ROOT/'evidence'/(name+'.html')).write_text(view,encoding='utf-8')
    options=webdriver.ChromeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1440,1000')
    driver=webdriver.Remote(os.getenv('SELENIUM_URL','http://127.0.0.1:4447/wd/hub'),options=options)
    base=os.getenv('EVIDENCE_URL','http://panchyshkin-ipz-evidence:8090')
    pages=[('startup','/full/surefire/ua.edu.kntu.jobs.StartupTest.txt','Tests run: 1'),
           ('unit','/full/surefire/ua.edu.kntu.jobs.ValidationTest.txt','Tests run:'),
           ('integration','/full/surefire/ua.edu.kntu.jobs.SQLiteHttpIntegrationTest.txt','Tests run:'),
           ('regression','/lab10-detected-regression.html','FAILURE'),
           ('regression-fixed','/lab10-fixed-regression.html','BUILD SUCCESS'),
           ('coverage','/full/jacoco/index.html','Total'),
           ('coverage-baseline','/baseline/jacoco/index.html','Total'),
           ('bdd','/full/cucumber.html','6 PASSED'),
           ('load','/load/html-15/index.html','Dashboard')]
    try:
        for name,path,expected in pages:
            driver.get(base+path)
            WebDriverWait(driver,20).until(lambda d:expected.lower() in d.find_element(By.TAG_NAME,'body').text.lower())
            if name in ['startup','unit','integration']:
                driver.execute_script("const p=document.querySelector('pre'); if(p){p.style.fontSize='24px';p.style.whiteSpace='pre-wrap';p.style.overflowWrap='anywhere';}")
            if name=='regression-fixed':
                driver.execute_script('window.scrollTo(0, document.body.scrollHeight)')
            driver.save_screenshot(str(E/(name+'.png')))
            if name in ['startup','unit','integration']:
                from PIL import Image,ImageChops
                path=E/(name+'.png')
                with Image.open(path).convert('RGB') as im:
                    bounds=ImageChops.difference(im,Image.new('RGB',im.size,'white')).getbbox()
                    if bounds:im.crop((max(0,bounds[0]-12),max(0,bounds[1]-12),min(im.width,bounds[2]+12),min(im.height,bounds[3]+12))).save(path)
            print('Captured',name,flush=True)
    finally:driver.quit()
if __name__=='__main__':run()
