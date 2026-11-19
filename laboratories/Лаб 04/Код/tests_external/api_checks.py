"""REST and security tests against the locally deployed IPZ application."""
from pathlib import Path
import json,os,time,urllib.request,urllib.error,http.cookiejar,uuid
ROOT=Path(__file__).resolve().parents[1]
BASE=os.getenv('IPZ_BASE_URL','http://127.0.0.1:18727')
E=ROOT/'evidence';E.mkdir(exist_ok=True)
class Client:
    def __init__(self):
        self.cookies=http.cookiejar.CookieJar()
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
    def request(self,method,path,body=None,headers=None):
        h={'Content-Type':'application/json'} if body is not None else {}
        h.update(headers or {})
        req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,headers=h,method=method)
        start=time.perf_counter()
        try:r=self.opener.open(req,timeout=10)
        except urllib.error.HTTPError as error:r=error
        payload=r.read();data=json.loads(payload) if payload else {}
        return r.status,data,dict(r.headers),round((time.perf_counter()-start)*1000,2)
def run():
    results=[];suffix=uuid.uuid4().hex[:8]
    def check(id,title,client,method,path,expected,body=None,verify=None,headers=None,kind='api'):
        status,data,response_headers,ms=client.request(method,path,body,headers)
        assert status==expected,(id,status,data)
        if verify:assert verify(data,response_headers),(id,data)
        results.append({'id':id,'name':title,'method':method,'path':path,'expected_status':expected,'actual_status':status,'ms':ms,'status':'PASS','kind':kind})
        print(id,status,'PASS',flush=True)
        return data,response_headers
    anon=Client();seeker=Client();employer=Client();other=Client()
    check('API01','Health',anon,'GET','/api/health',200,verify=lambda d,h:d['status']=='ok')
    check('API02','Catalog schema',anon,'GET','/api/jobs',200,verify=lambda d,h:isinstance(d['jobs'],list) and d['count']==len(d['jobs']))
    check('API03','Unauthorized profile',anon,'GET','/api/me',401)
    account={'name':'Богдан Панчишкін','email':f'api-{suffix}@example.test','password':'TestPass26!','role':'SEEKER'}
    check('API04','Register seeker',seeker,'POST','/api/auth/register',201,account,lambda d,h:d['user']['role']=='SEEKER' and 'password' not in d['user'])
    check('API05','Duplicate email',anon,'POST','/api/auth/register',409,account)
    login={'email':account['email'],'password':account['password']}
    check('API06','Login cookie',seeker,'POST','/api/auth/login',200,login,lambda d,h:'HttpOnly' in h.get('Set-cookie',h.get('Set-Cookie','')))
    check('API07','Authenticated profile',seeker,'GET','/api/me',200,verify=lambda d,h:d['user']['email']==account['email'])
    check('API08','Missing resume',seeker,'GET','/api/me/resume',404)
    resume={'title':'Java QA Engineer','text':'Досвід тестування API, JUnit і Selenium у навчальному проєкті.','skills':'Java, SQL'}
    check('API09','Create resume',seeker,'PUT','/api/me/resume',200,resume,lambda d,h:d['resume']['title']==resume['title'])
    check('API10','Invalid resume rejected',seeker,'PUT','/api/me/resume',400,{**resume,'title':'x'})
    check('API11','Resume remains unchanged',seeker,'GET','/api/me/resume',200,verify=lambda d,h:d['resume']['title']==resume['title'])
    vacancy={'title':f'API QA Vacancy {suffix}','company':'IPZ Company','city':'Київ','salary':40000,'remote':True,'description':'Тестова вакансія для перевірки HTTP та збереження у SQLite.'}
    check('API12','Seeker cannot publish',seeker,'POST','/api/jobs',403,vacancy)
    owner={**account,'name':'Роботодавець','email':f'employer-{suffix}@example.test','role':'EMPLOYER'}
    check('API13','Register employer',employer,'POST','/api/auth/register',201,owner)
    check('API14','Employer login',employer,'POST','/api/auth/login',200,{'email':owner['email'],'password':owner['password']})
    job,_=check('API15','Create vacancy',employer,'POST','/api/jobs',201,vacancy,lambda d,h:d['job']['salary']==40000)
    id=job['job']['id']
    check('API16','Vacancy details',anon,'GET',f'/api/jobs/{id}',200,verify=lambda d,h:d['job']['title']==vacancy['title'])
    check('API17','Create application',seeker,'POST',f'/api/jobs/{id}/applications',201,{'coverLetter':'Готовий до співбесіди.'})
    check('API18','Duplicate application',seeker,'POST',f'/api/jobs/{id}/applications',409,{'coverLetter':'Повторний відгук.'})
    check('API19','Exactly one application',seeker,'GET','/api/me/applications',200,verify=lambda d,h:d['count']==1)
    check('API20','Seeker cannot close vacancy',seeker,'PATCH',f'/api/jobs/{id}',403,{'status':'CLOSED'})
    check('API21','Owner closes vacancy',employer,'PATCH',f'/api/jobs/{id}',200,{'status':'CLOSED'},lambda d,h:d['job']['status']=='CLOSED')
    check('API22','Unknown query parameter',anon,'GET','/api/jobs?unexpected=1',400)
    check('API23','Unknown resource',anon,'GET','/api/unknown',404)
    check('API24','Wrong HTTP method',anon,'DELETE','/api/jobs',405)
    check('API25','Logout',seeker,'POST','/api/auth/logout',200)
    check('API26','Session invalidated',seeker,'GET','/api/me',401)
    # Security payloads target only this local test application.
    check('SEC01','SQL injection password does not authenticate',anon,'POST','/api/auth/login',401,{'email':owner['email'],'password':"' OR 1=1 --"},kind='security')
    from urllib.parse import urlencode
    check('SEC02','SQL search payload remains literal',anon,'GET','/api/jobs?'+urlencode({'query':"' OR 1=1 --"}),200,verify=lambda d,h:d['count']==0,kind='security')
    check('SEC03','Catalog remains available',anon,'GET','/api/jobs',200,verify=lambda d,h:d['count']>=3,kind='security')
    check('SEC04','Foreign Origin rejected',anon,'POST','/api/auth/login',403,login,headers={'Origin':'http://untrusted.example'},kind='security')
    check('SEC05','Security response headers',anon,'GET','/api/health',200,verify=lambda d,h:h.get('X-content-type-options',h.get('X-Content-Type-Options'))=='nosniff' and any(k.lower()=='content-security-policy' for k in h),kind='security')
    check('SEC06','No session for anonymous request',anon,'PUT','/api/me/resume',401,resume,kind='security')
    (E/'api-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print('API and security checks:',len(results),'PASS',flush=True)
if __name__=='__main__':run()
