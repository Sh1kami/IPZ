'use strict';
const $ = id => document.getElementById(id);
let currentUser = null;
let selectedJobId = null;
function node(tag, text, cls) { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; }
function message(text, error = false) { $('message').textContent = text; $('message').className = error ? 'error' : ''; }
async function request(method, path, data) {
  const options = {method, credentials:'same-origin', headers:{}};
  if (data !== undefined) { options.headers['Content-Type'] = 'application/json'; options.body = JSON.stringify(data); }
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) { const error = new Error(body.error?.message || 'Помилка запиту.'); error.code = body.error?.code; error.status = response.status; throw error; }
  return body;
}
function formData(id) { return Object.fromEntries(new FormData($(id)).entries()); }
function handle(formId, action) {
  $(formId).addEventListener('submit', async event => {
    event.preventDefault(); const button = $(formId).querySelector('button[type=submit]'); button.disabled = true;
    try { await action(); } catch (error) { message(error.message, true); } finally { button.disabled = false; }
  });
}
async function refreshAccount() {
  try { currentUser = (await request('GET','/api/me')).user; } catch (e) { if(e.status !== 401) throw e; currentUser = null; }
  $('guest-panels').hidden = Boolean(currentUser); $('signed-in').hidden = !currentUser;
  const seeker = currentUser?.role === 'SEEKER', employer = currentUser?.role === 'EMPLOYER';
  $('resume-panel').hidden = !seeker; $('applications-panel').hidden = !seeker; $('employer-panel').hidden = !employer;
  $('current-user').textContent = currentUser ? `${currentUser.name} · ${currentUser.email} · ${seeker ? 'Шукач роботи' : 'Роботодавець'}` : '';
  if (seeker) {
    $('resume-form').reset();
    try { const {resume} = await request('GET','/api/me/resume'); $('resume-title').value = resume.title; $('resume-text').value = resume.text; $('resume-skills').value = resume.skills; }
    catch(e) { if(e.code !== 'RESUME_NOT_FOUND') throw e; }
    await refreshApplications();
  }
  await refreshJobs();
}
async function refreshApplications() {
  const {applications} = await request('GET','/api/me/applications'); const list = $('applications-list'); list.replaceChildren();
  if (!applications.length) list.append(node('p','Ви ще не надсилали відгуків.'));
  for (const app of applications) {
    const card = node('article',undefined,'application-card'); card.append(node('h3',app.jobTitle),node('p',app.company),node('p',`Резюме: ${app.resumeTitle}`),node('p',`Надіслано: ${new Date(app.createdAt).toLocaleString('uk-UA')}`));
    if(app.coverLetter) card.append(node('p',app.coverLetter,'prewrap')); list.append(card);
  }
}
async function refreshJobs() {
  const params = new URLSearchParams(); for (const [key,value] of Object.entries(formData('search-form'))) if(value !== '') params.set(key,value);
  const {jobs,count} = await request('GET','/api/jobs?' + params.toString()); $('job-count').textContent = `Знайдено вакансій: ${count}`;
  const list = $('jobs-list'); list.replaceChildren(); if(!jobs.length) list.append(node('p','За цими умовами вакансій немає.'));
  for (const job of jobs) {
    const card = node('article',undefined,'job-card'); card.dataset.jobId = String(job.id);
    card.append(node('h3',job.title),node('p',job.company),node('p',`${job.city} · ${job.remote ? 'Віддалено' : 'В офісі'} · ${job.salary.toLocaleString('uk-UA')} грн`),node('p',job.status === 'ACTIVE' ? 'Відкрита' : 'Закрита'));
    const details = node('button','Деталі','secondary'); details.type='button'; details.dataset.testid='job-details'; details.addEventListener('click',()=>showDetails(job.id).catch(e=>message(e.message,true))); card.append(details);
    if(currentUser?.role === 'SEEKER') { const apply = node('button','Відгукнутися'); apply.type='button'; apply.dataset.testid='job-apply'; apply.disabled=job.status!=='ACTIVE'; apply.addEventListener('click',()=>openApplication(job)); card.append(apply); }
    if(currentUser?.role === 'EMPLOYER' && job.ownerId === currentUser.id) {
      const toggle = node('button',job.status==='ACTIVE' ? 'Закрити вакансію' : 'Відкрити вакансію','secondary'); toggle.type='button'; toggle.dataset.testid='job-toggle-status';
      toggle.addEventListener('click',async()=>{ try { await request('PATCH',`/api/jobs/${job.id}`,{status:job.status==='ACTIVE'?'CLOSED':'ACTIVE'}); message('Стан вакансії змінено.'); await refreshJobs(); } catch(e){message(e.message,true);} }); card.append(toggle);
    }
    list.append(card);
  }
}
async function showDetails(id) {
  const {job} = await request('GET',`/api/jobs/${id}`); $('detail-title').textContent=job.title;
  $('detail-content').replaceChildren(node('p',job.company),node('p',`${job.city} · ${job.salary} грн · ${job.remote?'Віддалено':'В офісі'}`),node('p',job.description,'prewrap')); $('job-dialog').showModal();
}
function openApplication(job) { selectedJobId=job.id; $('apply-job-title').textContent=job.title; $('apply-form').reset(); $('apply-error').textContent=''; $('apply-dialog').showModal(); }
handle('register-form', async()=>{ const data=formData('register-form'); const {user}=await request('POST','/api/auth/register',data); $('register-form').reset(); $('login-email').value=user.email; message('Обліковий запис створено. Тепер увійдіть.'); });
handle('login-form', async()=>{ await request('POST','/api/auth/login',formData('login-form')); $('login-password').value=''; await refreshAccount(); message('Вхід виконано.'); });
$('logout-button').addEventListener('click',async()=>{try{await request('POST','/api/auth/logout'); await refreshAccount(); message('Ви вийшли з облікового запису.');}catch(e){message(e.message,true);}});
handle('resume-form',async()=>{await request('PUT','/api/me/resume',formData('resume-form')); message('Резюме збережено.');});
handle('job-form',async()=>{const data=formData('job-form'); data.salary=Number(data.salary); data.remote=$('job-remote').checked; await request('POST','/api/jobs',data); $('job-form').reset(); await refreshJobs(); message('Вакансію опубліковано.');});
handle('search-form',async()=>{await refreshJobs(); message('Умови пошуку застосовано.');});
$('search-reset').addEventListener('click',async()=>{$('search-form').reset(); try{await refreshJobs(); message('Умови пошуку скинуто.');}catch(e){message(e.message,true);}});
$('detail-close').addEventListener('click',()=>$('job-dialog').close()); $('apply-cancel').addEventListener('click',()=>$('apply-dialog').close());
$('apply-form').addEventListener('submit',async event=>{event.preventDefault(); $('apply-confirm').disabled=true; try{await request('POST',`/api/jobs/${selectedJobId}/applications`,formData('apply-form')); $('apply-dialog').close(); await refreshApplications(); message('Відгук надіслано.');}catch(e){$('apply-error').textContent=e.message;}finally{$('apply-confirm').disabled=false;}});
refreshAccount().catch(error=>message(error.message,true));
