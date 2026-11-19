"""Native Android tests through Appium and UiAutomator2."""
from pathlib import Path
import json,os,subprocess,time
from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import NoSuchElementException,StaleElementReferenceException
ROOT=Path(__file__).resolve().parents[1];E=ROOT/'evidence/android';E.mkdir(exist_ok=True)
CONTAINER=os.getenv('ANDROID_CONTAINER','panchyshkin-ipz-mobile')
def adb(*args):
    r=subprocess.run(['docker','exec',CONTAINER,'adb',*args],capture_output=True,timeout=120)
    assert r.returncode==0,r.stderr.decode(errors='replace')
    return r.stdout.decode('utf-8',errors='replace').strip()
def run():
    results=[]
    for attempt in range(120):
        try:
            if adb('shell','getprop','sys.boot_completed')=='1':break
        except Exception:pass
        time.sleep(5)
    else:raise RuntimeError('Android did not complete boot')
    adb('shell','input','keyevent','82')
    props={key:adb('shell','getprop',key) for key in ['ro.build.version.release','ro.build.version.sdk','ro.product.model','ro.product.cpu.abi']}
    (E/'device.json').write_text(json.dumps(props,indent=2),encoding='utf-8')
    for profile,size in [('phone','480x800'),('tablet','800x1280')]:
        adb('shell','wm','size',size);adb('shell','wm','density','160')
        caps={'platformName':'Android','appium:automationName':'UiAutomator2','appium:deviceName':'emulator-5554','appium:udid':'emulator-5554',
              'appium:app':'/workspace/mobile/app/build/outputs/apk/debug/app-debug.apk',
              'appium:appPackage':'ua.edu.kntu.panchyshkin.ipz','appium:appActivity':'.MainActivity',
              'appium:noReset':False,'appium:disableWindowAnimation':True,'appium:newCommandTimeout':180,
              'appium:adbExecTimeout':120000,'appium:uiautomator2ServerInstallTimeout':180000,
              'appium:uiautomator2ServerLaunchTimeout':180000,'appium:androidInstallTimeout':180000}
        driver=webdriver.Remote(os.getenv('APPIUM_URL','http://127.0.0.1:4729'),options=UiAutomator2Options().load_capabilities(caps))
        wait=WebDriverWait(driver,100,ignored_exceptions=(NoSuchElementException,StaleElementReferenceException))
        def el(id):return driver.find_element(AppiumBy.ACCESSIBILITY_ID,id)
        def count(n):wait.until(lambda d:el('job-count').text==f'Знайдено вакансій: {n}')
        def passed(id,name):
            results.append({'id':id,'profile':profile,'screen':size,'name':name,'status':'PASS'})
            (E/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');print(profile,id,'PASS',flush=True)
        def search(value):
            box=el('search-query');box.clear();box.send_keys(value)
            try:driver.hide_keyboard()
            except Exception:pass
            el('search-submit').click()
        try:
            count(3);passed('MOB01','Launch and initial catalog')
            driver.save_screenshot(str(E/(profile+'-catalog.png')))
            search('JAVA');count(2);passed('MOB02','Case insensitive search')
            el('job-1').click();wait.until(lambda d:d.find_element(AppiumBy.ID,'android:id/alertTitle').text=='Java Developer')
            driver.find_element(AppiumBy.ID,'android:id/button1').click();passed('MOB03','Open and close vacancy details')
            driver.orientation='LANDSCAPE';count(2);assert el('search-query').text=='JAVA'
            driver.save_screenshot(str(E/(profile+'-landscape.png')));passed('MOB04','Search state survives rotation')
            driver.orientation='PORTRAIT';count(2)
            search('Rust');count(0);passed('MOB05','Empty search result')
            el('search-reset').click();count(3);passed('MOB06','Reset restores catalog')
            driver.save_screenshot(str(E/(profile+'-final.png')))
        except Exception:
            driver.save_screenshot(str(E/(profile+'-failure.png')))
            (E/(profile+'-failure.xml')).write_text(driver.page_source,encoding='utf-8')
            raise
        finally:driver.quit()
    print('Mobile checks:',len(results),'PASS',flush=True)
if __name__=='__main__':run()
