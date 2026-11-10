'use strict';
const $=s=>document.querySelector(s),app=$('#app');
const state={user:null,today:null,patients:[],patientId:null,plans:[],planId:null,detail:null,progress:null,exercises:[],tab:'overview'};
const names={ADMIN:'Администратор',SPECIALIST:'Специалист',PATIENT:'Пациент',DRAFT:'Черновик',ACTIVE:'Активный',COMPLETED:'Завершён',ARCHIVED:'В архиве',DONE:'Выполнено',SKIPPED:'Пропущено'};
const units={MINUTES:'мин',REPETITIONS:'повт.',METERS:'м'};
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt=d=>d?new Date(d+'T12:00:00').toLocaleDateString('ru-RU',{day:'numeric',month:'short',year:'numeric'}):'—';
const num=n=>new Intl.NumberFormat('ru-RU',{maximumFractionDigits:1}).format(n??0);
const staff=()=>state.user?.role!=='PATIENT';
const badge=s=>'<span class="badge '+esc(s)+'">'+esc(names[s]||s)+'</span>';
let csrf=null;
async function token(){const r=await fetch('/api/auth/csrf');if(!r.ok)throw Error('Не удалось получить токен формы');csrf=await r.json();}
async function api(path,method='GET',body){
 const headers={};if(method!=='GET'){if(!csrf)await token();headers[csrf.headerName]=csrf.token;if(body!==undefined)headers['Content-Type']='application/json';}
 const r=await fetch('/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok){let text;try{text=(await r.json()).detail}catch{text='Не удалось выполнить запрос'}if(r.status===401&&state.user){state.user=null;csrf=null;$('#modal').close();showHome();}throw Error(text||'Ошибка '+r.status);}
 return r.status===204?null:r.json();
}
function toast(text){const t=$('#toast');t.textContent=text;t.style.display='block';clearTimeout(toast.timer);toast.timer=setTimeout(()=>t.style.display='none',4500);}
function brand(){return '<div class="brand"><img src="/favicon.svg" alt=""><div>Точка опоры<small>ДНЕВНИК РЕАБИЛИТАЦИИ</small></div></div>';}
function home(){
 const enter=state.user?'Личный кабинет':'Войти';
 app.innerHTML=`<div class="landing" id="home">
  <header class="public-header"><div class="public-wrap public-bar">
   <a class="public-brand" href="#home" aria-label="Точка опоры — главная">${brand()}</a>
   <nav aria-label="Разделы главной"><a href="#about">О сервисе</a><a href="#benefits">Для кого</a><a href="#contacts">Контакты</a></nav>
   <div class="actions public-auth">${state.user?'':'<button data-register>Регистрация</button>'}<button class="primary" data-enter>${enter}</button></div>
  </div></header>
  <main>
   <section class="public-wrap public-hero" aria-labelledby="welcome">
    <div><div class="eyebrow">Дневник реабилитации после травм и инсультов</div>
    <h1 id="welcome">Восстановление<br>в вашем ритме</h1>
    <p>Точка опоры помогает следовать плану занятий, замечать изменения в самочувствии и обсуждать ход восстановления со специалистом.</p>
    <div class="actions"><button class="primary" data-enter>${state.user?'Открыть кабинет':'Войти в дневник'}</button>${state.user?'':'<button data-register>Создать аккаунт</button>'}</div></div>
    <ol class="public-steps"><li><span>01</span><div><strong>Получите план</strong><br>Специалист подбирает упражнения и задаёт нагрузку.</div></li><li><span>02</span><div><strong>Записывайте результаты</strong><br>Отмечайте занятия, боль и самочувствие.</div></li><li><span>03</span><div><strong>Смотрите динамику</strong><br>Сравнивайте выполненные занятия с планом.</div></li></ol>
   </section>
   <section id="about" class="public-band"><div class="public-wrap"><div class="eyebrow">О сервисе</div><h2>План, дневник и прогресс в одном месте</h2><p>На занятии легко вспомнить, что получилось, а где возникли трудности. Дневник сохраняет эти наблюдения между визитами, чтобы пациент и специалист могли обсуждать конкретные записи. Нагрузку и её изменение определяет специалист.</p></div></section>
   <section id="benefits" class="public-wrap public-benefits"><div class="eyebrow">Для кого</div><h2>У каждого своя задача</h2><div class="public-grid">
    <article><h3>Пациентам</h3><p>Видеть, что запланировано, отмечать занятия и понимать, насколько регулярно удаётся выполнять план.</p></article>
    <article><h3>Врачам и специалистам</h3><p>Составлять планы, просматривать записи и динамику закреплённых пациентов, оставлять обратную связь.</p></article>
    <article><h3>Координаторам</h3><p>Добавлять специалистов, распределять пациентов и проверять изменения в журнале действий.</p></article>
   </div></section>
  </main>
  <footer id="contacts" class="public-footer"><div class="public-wrap public-bar"><div>${brand()}<small>Учебный проект · демонстрационные контакты</small></div><address><strong>Связаться с командой</strong><br><a href="mailto:hello@tochka-opory.example">hello@tochka-opory.example</a></address></div></footer>
 </div>`;
 bind('[data-enter]',()=>state.user?openCabinet():login());
 bind('[data-register]',registrationForm);
}
function openCabinet(){if(location.hash==='#cabinet')return start();location.hash='cabinet';}
function showHome(){location.hash='home';home();window.scrollTo(0,0);}
function login(username='',message=''){
 modal('Войти в дневник',
  (message?'<p class="auth-success" role="status">'+esc(message)+'</p>':'')+
  field('Email или логин','username','text',username,'required autocomplete="username"')+
  field('Пароль','password','password','','required autocomplete="current-password"')+
  '<p class="form-hint">Впервые здесь? <button type="button" class="btn-link" id="switch-register">Создать аккаунт пациента</button></p>'+
  '<div class="demo">Демонстрационный вход<div class="actions"><button type="button" data-demo="patient">Пациент</button><button type="button" data-demo="doctor">Специалист</button><button type="button" data-demo="admin">Администратор</button></div></div>', 
  async data=>{
   await token();
   const r=await fetch('/api/auth/login',{method:'POST',headers:{[csrf.headerName]:csrf.token,'Content-Type':'application/x-www-form-urlencoded'},
    body:new URLSearchParams({...data,username:data.username.trim().toLowerCase()})});
   if(!r.ok)throw Error((await r.json()).detail||'Ошибка входа');
   csrf=null;state.tab='overview';openCabinet();
  },'Войти');
 $('#switch-register').onclick=registrationForm;
 const demoPasswords={patient:'Patient2026!',doctor:'Doctor2026!',admin:'Admin2026!'};
 bind('[data-demo]',b=>{
  const form=$('#edit-form'),submit=form.querySelector('button[type=submit]');
  if(submit.disabled)return;
  form.elements.username.value=b.dataset.demo;
  form.elements.password.value=demoPasswords[b.dataset.demo];
  form.requestSubmit(submit);
 });
}
function credentialsFields(){
 return field('ФИО (фамилия, имя, отчество)','fullName','text','','required maxlength="150" autocomplete="name"')+
  field('Email','username','email','','required maxlength="254" autocomplete="email"')+
  field('Пароль','password','password','','required minlength="10" maxlength="72" autocomplete="new-password"')+
  '<p class="form-hint">От 10 символов: строчная и заглавная буквы, цифра и спецсимвол. Максимум 72 байта UTF-8.</p>'+
  field('Повторите пароль','passwordConfirmation','password','','required maxlength="72" autocomplete="new-password"');
}
function checkCredentials(data){
 data.username=data.username.trim().toLowerCase();
 if(data.password!==data.passwordConfirmation)throw Error('Пароли не совпадают');
 if(!/\p{Ll}/u.test(data.password)||!/\p{Lu}/u.test(data.password)||!/\p{Nd}/u.test(data.password)||
  !/[^\p{L}\p{N}\s\p{Cc}]/u.test(data.password))throw Error('Добавьте строчную и заглавную буквы, цифру и спецсимвол.');
 if(new TextEncoder().encode(data.password).length>72)throw Error('Пароль занимает больше 72 байт UTF-8.');
}
function registrationForm(){
 modal('Регистрация пациента',credentialsFields()+
  field('Дата рождения','birthDate','date','','required autocomplete="bday"')+
  field('Причина реабилитации (необязательно)','rehabilitationReason','text','','maxlength="200"')+
  '<p class="form-hint">После регистрации войдите с новым паролем. Специалиста назначит администратор. <button type="button" class="btn-link" id="switch-login">Уже есть аккаунт</button></p>',
  async data=>{checkCredentials(data);await api('/auth/register','POST',data);login(data.username,'Аккаунт создан. Теперь войдите с новым паролем.');},'Зарегистрироваться');
 $('#switch-login').onclick=()=>login();
}
async function start(me){me??=await api('/auth/me');state.user=me.user;state.today=me.today;state.patients=await api('/patients');state.exercises=await api('/exercises');
 if(!state.patients.some(p=>p.id===state.patientId))state.patientId=state.patients[0]?.id??null;
 if(!staff()&&['patients','exercises','audit'].includes(state.tab))state.tab='overview';
 if(state.user.role!=='ADMIN'&&['audit','specialists'].includes(state.tab))state.tab='overview';
 await loadPlans();if(location.hash==='#cabinet')render();
}
async function loadPlans(){state.plans=state.patientId?await api('/patients/'+state.patientId+'/plans'):[];
 if(!state.plans.some(p=>p.id===state.planId))state.planId=(state.plans.find(p=>p.status==='ACTIVE')||state.plans[0])?.id??null;await loadDetail();}
async function loadDetail(){if(state.planId){[state.detail,state.progress]=await Promise.all([api('/plans/'+state.planId),api('/plans/'+state.planId+'/progress')]);}else{state.detail=null;state.progress=null;}}
function patient(){return state.patients.find(p=>p.id===state.patientId);}
function bind(selector,action){document.querySelectorAll(selector).forEach(b=>b.onclick=()=>Promise.resolve().then(()=>action(b)).catch(e=>toast(e.message)));}
function render(){
 const tabs=[['overview','Обзор'],['plans','Планы реабилитации'],['diary','Дневник занятий'],['progress',staff()?'Прогресс пациента':'Мой прогресс']];if(staff())tabs.push(['patients','Пациенты'],['exercises','Упражнения']);if(state.user.role==='ADMIN')tabs.push(['specialists','Специалисты'],['audit','Журнал действий']);
 tabs.push(['chat','Сообщения']);
 const contextual=['overview','plans','diary','progress'].includes(state.tab);
 const picker=!contextual?'':staff()&&state.patients.length?'<label>Пациент <select id="patient-picker" aria-label="Выбрать пациента">'+state.patients.map(p=>'<option value="'+p.id+'" '+(p.id===state.patientId?'selected':'')+'>'+esc(p.fullName)+'</option>').join('')+'</select></label>':staff()?'':'<p>Ваш специалист: '+esc(patient()?.specialistName||'не назначен')+'</p>';
 app.innerHTML='<div class="shell"><aside class="sidebar">'+brand()+'<nav aria-label="Основное меню">'+tabs.map(([id,t])=>'<button class="nav '+(state.tab===id?'active':'')+'" data-tab="'+id+'">'+t+(id==='chat'?'<span id="chat-badge" class="unread-dot" hidden></span>':'')+'</button>').join('')+'</nav><div class="sidebar-note">Маленькие шаги.<br>Заметные изменения.<p style="margin-top:18px">Учебная система учёта.<br>Нагрузку назначает специалист.</p></div><div class="user"><strong>'+esc(state.user.fullName)+'</strong><small>'+names[state.user.role]+'</small></div></aside><main class="main"><header class="topbar"><p>● '+fmt(state.today)+'</p>'+picker+'<div class="actions"><a class="btn-link" href="#home">На главную</a><button class="btn-link" id="logout">Выйти</button></div></header><div id="content"></div></main></div>';
 bind('[data-tab]',b=>{state.tab=b.dataset.tab;render();});
 $('#logout').onclick=async()=>{try{await api('/auth/logout','POST');state.user=null;chatKey=null;state.patientId=null;state.planId=null;state.tab='overview';csrf=null;showHome();}catch(e){toast(e.message)}};
 if($('#patient-picker'))$('#patient-picker').onchange=async e=>{state.patientId=Number(e.target.value);state.planId=null;try{await loadPlans();render()}catch(e){toast(e.message)}};
 ({overview,plans:plansPage,diary,progress:progressPage,patients:patientsPage,exercises:exercisesPage,specialists:specialistsPage,audit:auditPage,chat:chatPage}[state.tab]||overview)();
 if(state.tab!=='chat')void refreshThreads();
}
function heading(title,description,buttons=''){return '<div class="page-head"><div><div class="eyebrow">Дневник реабилитации</div><h1>'+title+'</h1><p>'+description+'</p></div><div class="actions">'+buttons+'</div></div>';}
function empty(text,sub=''){return '<div class="empty"><strong>'+esc(text)+'</strong>'+esc(sub)+'</div>';}
function stats(){const p=state.progress||{};return '<div class="stats">'+[['Регулярность занятий',num(p.adherence)+'%','выполнено / запланировано'],['Выполнено занятий',(p.done||0)+' / '+(p.scheduled||0),'за прошедшие дни плана'],['Целевая нагрузка достигнута',p.targetReached||0,'занятий с фактом не ниже цели'],['Пока без записи',p.unrecorded||0,'пропущено явно: '+(p.skipped||0)]].map(([t,n,s])=>'<div class="stat"><span class="stat-label">'+t+'</span><strong>'+n+'</strong><small>'+s+'</small></div>').join('')+'</div>';}
function wireGo(){bind('[data-go]',b=>{state.tab=b.dataset.go;render()});}
function givenName(fullName){
 const [surname,firstName]=fullName.trim().split(/\s+/);
 return firstName||surname;
}
function overview(){
 const p=state.detail?.plan,items=state.detail?.assignments||[];
 let tasks=empty('Сегодня нет назначенных занятий','Проверьте период и состояние выбранного плана.');
 if(p?.status==='ACTIVE'&&p.startsOn<=state.today&&p.endsOn>=state.today)tasks=items.map(a=>{const e=state.detail.entries.find(e=>e.assignmentId===a.id&&e.performedOn===state.today);return '<div class="task"><div><div class="task-title"><span class="dot"></span>'+esc(a.title)+'</div><small>Цель: '+num(a.targetAmount)+' '+units[a.unit]+'</small></div>'+(e?badge(e.status):staff()?'<span class="badge">Без записи</span>':'<button data-entry="'+a.id+'">Отметить →</button>')+'</div>';}).join('');
 $('#content').innerHTML=heading(staff()?'Обзор восстановления':'Здравствуйте, '+esc(givenName(state.user.fullName))+'!','План, занятия и самочувствие — всё важное перед вами.','<button class="primary" data-go="'+(staff()?'plans':'diary')+'">'+(staff()?'Открыть планы':'Записать занятие')+' →</button>')+
 '<section class="hero"><div><div class="eyebrow">'+(p?names[p.status]+' · '+fmt(p.startsOn)+' — '+fmt(p.endsOn):'Начало пути')+'</div><h2>'+esc(p?.title||'План пока не создан')+'</h2><p>'+esc(p?.goal||(staff()?'Создайте план и добавьте индивидуальные назначения.':'Специалист подготовит для вас план занятий.'))+'</p></div><div class="hero-mark" aria-hidden="true">↗</div></section>'+stats()+
 '<div class="grid"><section class="card"><div class="card-head"><h2>Занятия на сегодня</h2><small>'+items.length+' назначений</small></div>'+tasks+'<div class="note">Изменения программы обсуждайте со своим специалистом.</div></section><section class="card"><div class="card-head"><h2>Ритм занятий</h2></div>'+[['Выполнено',state.progress?.adherence],['С достижением цели',state.progress?.targetAchievement]].map(([t,n])=>'<div class="summary-line"><span>'+t+'</span><strong>'+num(n)+'%</strong></div><div class="track"><span style="width:'+Math.min(100,n||0)+'%"></span></div>').join('')+'<p class="note">Это показатели ведения дневника и выполнения плана. Они не являются процентом выздоровления.</p><button data-go="progress">Посмотреть динамику →</button></section></div>';
 wireGo();bind('[data-entry]',b=>entryForm(Number(b.dataset.entry)));
}
function selector(){return '<div class="plan-select"><label for="plan-picker">План</label><select id="plan-picker">'+state.plans.map(p=>'<option value="'+p.id+'" '+(p.id===state.planId?'selected':'')+'>'+esc(p.title)+' · '+names[p.status]+'</option>').join('')+'</select>'+(state.planId?'<a href="/api/plans/'+state.planId+'/export" download>Скачать CSV ↓</a>':'')+'</div>';}
function wireSelector(){if($('#plan-picker'))$('#plan-picker').onchange=async e=>{state.planId=Number(e.target.value);try{await loadDetail();render()}catch(e){toast(e.message)}};}
function plansPage(){
 const p=state.detail?.plan;
 let html=heading('Планы реабилитации','Индивидуальные цели и назначения. История завершённых планов сохраняется.',staff()&&patient()?'<button class="primary" id="new-plan">+ Создать план</button>':'');
 html+=state.plans.length?'<div class="cards">'+state.plans.map(x=>'<section class="card '+(x.id===state.planId?'selected':'')+'"><div class="card-head">'+badge(x.status)+'<small>№ '+x.id+'</small></div><h2>'+esc(x.title)+'</h2><p class="muted">'+esc(x.goal)+'</p><p><small>'+fmt(x.startsOn)+' — '+fmt(x.endsOn)+'</small></p><button data-plan="'+x.id+'">'+(x.id===state.planId?'Выбран':'Открыть назначения')+' →</button></section>').join('')+'</div>':empty('Планов пока нет','Специалист может создать первый план для пациента.');
 if(p?.finished)html+=courseReview(p,!staff());
 if(p){let actions='';if(staff()){if(p.status==='DRAFT')actions='<button class="primary" data-status="ACTIVE">Активировать план</button><button data-status="ARCHIVED">В архив</button>';if(p.status==='ACTIVE')actions='<button data-status="COMPLETED">Завершить план</button>';if(p.status==='COMPLETED')actions='<button data-status="ARCHIVED">В архив</button>';}
 html+='<section class="card"><div class="card-head"><h2>Назначения · '+esc(p.title)+'</h2>'+(staff()&&p.status==='DRAFT'?'<button id="add-assignment">+ Упражнение</button>':'')+'</div><p class="form-hint">Каждое упражнение назначается один раз в день на весь период плана. Минуты, метры и повторения учитываются отдельно.</p>'+
 (state.detail.assignments.length?state.detail.assignments.map(a=>'<div class="task"><div><div class="task-title">'+esc(a.title)+'</div><small>'+esc(a.instructions)+'</small><small>Ежедневная цель: '+num(a.targetAmount)+' '+units[a.unit]+'</small></div>'+(staff()&&p.status==='DRAFT'?'<button class="danger" data-remove="'+a.id+'">Убрать</button>':'')+'</div>').join(''):empty('Добавьте упражнения в черновик'))+'<div class="actions" style="margin-top:22px">'+actions+'</div></section>';}
 $('#content').innerHTML=html;
 bind('[data-review]',()=>modal('Отзыв о курсе',area('Как прошёл курс?','text',p.review||'','required maxlength="1000"'),
  async data=>{await api('/plans/'+p.id+'/review','PUT',data);await loadPlans();render();toast('Отзыв сохранён');}));
 if($('#new-plan'))$('#new-plan').onclick=planForm;if($('#add-assignment'))$('#add-assignment').onclick=assignmentForm;
 bind('[data-plan]',async b=>{state.planId=Number(b.dataset.plan);await loadDetail();render()});
 bind('[data-status]',b=>confirmAction('Изменить состояние плана',b.dataset.status==='ACTIVE'?'После активации назначения будут зафиксированы. Изменения программы оформляются новым планом.':'Изменить состояние? История сохранится. Завершённый или архивный план нельзя редактировать.',async()=>{await api('/plans/'+p.id+'/status','PATCH',{status:b.dataset.status,version:p.version});await loadPlans();render();toast('Состояние плана изменено');}));
 bind('[data-remove]',b=>confirmAction('Убрать назначение','Удалить упражнение из черновика?',async()=>{await api('/assignments/'+b.dataset.remove,'DELETE');await loadDetail();render();}));
}
function diary(){
 const p=state.detail?.plan;let html=heading('Дневник занятий','Отмечайте фактическую нагрузку и самочувствие. Пропуск можно записать с причиной.',p?.status==='ACTIVE'&&!staff()?'<button class="primary" id="new-entry">+ Записать занятие</button>':'')+selector();
 html+=p?'<section class="card"><div class="card-head"><h2>История занятий</h2>'+badge(p.status)+'</div>'+
 (state.detail.entries.length?'<div class="table-wrap"><table class="entries"><thead><tr><th>Дата и упражнение</th><th>Результат</th><th>Самочувствие</th><th>Запись и ответ специалиста</th><th>Действие</th></tr></thead><tbody>'+state.detail.entries.map(e=>'<tr><td>'+fmt(e.performedOn)+'<small>'+esc(e.exerciseTitle)+'</small></td><td>'+badge(e.status)+'<small>'+num(e.actualAmount)+' / '+num(e.targetAmount)+' '+units[e.unit]+'</small></td><td>Боль '+e.pain+'/10<small>Самочувствие '+e.wellbeing+'/10</small></td><td>'+(esc(e.comment)||'—')+(e.feedback?'<small><strong>Специалист:</strong> '+esc(e.feedback)+'</small>':'')+'</td><td>'+(staff()?'<button data-feedback="'+e.id+'">Ответить</button>':p.status==='ACTIVE'?'<button data-edit="'+e.id+'">Изменить</button>':'—')+'</td></tr>').join('')+'</tbody></table></div>':empty('Первые записи ещё впереди','Дневник заполняет пациент после активации плана.'))+'</section>':empty('Сначала нужен план реабилитации');
 $('#content').innerHTML=html;wireSelector();if($('#new-entry'))$('#new-entry').onclick=()=>entryForm();
 bind('[data-edit]',b=>entryForm(null,state.detail.entries.find(e=>e.id===Number(b.dataset.edit))));
 bind('[data-feedback]',b=>feedbackForm(state.detail.entries.find(e=>e.id===Number(b.dataset.feedback))));
}
function chart(points,key,target=null){
 if(!points.length)return empty('Пока нет выполненных занятий','График появится после первой записи.');
 const w=600,h=220,l=42,r=18,t=20,b=40,max=['pain','wellbeing'].includes(key)?10:Math.max(target||0,...points.map(p=>p[key]),1)*1.15;
 const dates=points.map(p=>Date.parse(p.date+'T12:00:00Z')),span=dates.at(-1)-dates[0];
 const x=i=>l+(w-l-r)*(span===0?.5:(dates[i]-dates[0])/span),y=v=>h-b-v/max*(h-t-b);
 const path=points.map((p,i)=>(i?'L':'M')+x(i)+','+y(p[key])).join(' ');
 return '<svg class="chart" viewBox="0 0 '+w+' '+h+'" role="img" aria-label="Динамика показателя по датам"><title>Фактические значения по датам</title>'+
 [0,.5,1].map(v=>'<line x1="'+l+'" y1="'+y(max*v)+'" x2="'+(w-r)+'" y2="'+y(max*v)+'" stroke="#e7ebe2"/><text x="'+(l-8)+'" y="'+(y(max*v)+4)+'" text-anchor="end">'+num(max*v)+'</text>').join('')+
 (target!==null?'<line x1="'+l+'" x2="'+(w-r)+'" y1="'+y(target)+'" y2="'+y(target)+'" stroke="#b7c3af" stroke-dasharray="5 5"/>':'')+
 '<path d="'+path+'" fill="none" stroke="#22675a" stroke-width="3" stroke-linejoin="round"/>'+
 points.map((p,i)=>'<circle cx="'+x(i)+'" cy="'+y(p[key])+'" r="4" fill="#22675a"><title>'+fmt(p.date)+': '+num(p[key])+'</title></circle>'+((i===0||i===points.length-1)?'<text x="'+x(i)+'" y="'+(h-10)+'" text-anchor="middle">'+esc(new Date(p.date+'T12:00:00').toLocaleDateString('ru-RU',{day:'numeric',month:'short'}))+'</text>':'')).join('')+'</svg>';
}
function progressPage(){
 const p=state.progress;$('#content').innerHTML=heading('Динамика восстановления','Регулярность, фактическая нагрузка и субъективное самочувствие.')+selector()+
 (p?stats()+p.series.map(s=>'<section class="card"><div class="card-head"><h2>'+esc(s.title)+'</h2><small>Цель '+num(s.targetAmount)+' '+units[s.unit]+'</small></div>'+chart(s.points,'amount',s.targetAmount)+'<div class="chart-legend"><span>Факт, '+units[s.unit]+'</span><span>Назначенная цель</span></div>'+
 (s.points.length?'<div class="summary-line"><span>Первая запись: '+num(s.points[0].amount)+' '+units[s.unit]+'</span><span>Последняя: '+num(s.points.at(-1).amount)+' '+units[s.unit]+'</span></div>':'')+
 '<details style="margin-top:15px"><summary>Боль и самочувствие по этому упражнению</summary><div class="grid"><div><h3>Боль · 0–10</h3>'+chart(s.points,'pain')+'</div><div><h3>Самочувствие · 1–10</h3>'+chart(s.points,'wellbeing')+'</div></div></details></section>').join('')+
 '<p class="muted">Пропуски не включаются в графики нагрузки. Плановые занятия считаются за прошедшие дни, включая сегодня. Эти показатели не определяют клинический результат.</p>':empty('Данные появятся после создания плана'));wireSelector();
}
function courseReview(course,editable=false){
 if(!course)return '';
 return '<section class="course-review"><span class="badge DONE">✓ Курс завершён</span><p><strong>'+esc(course.title)+'</strong></p>'+
 (course.review?'<blockquote>'+esc(course.review)+'</blockquote>':'<p class="muted">Отзыва пока нет</p>')+
 (editable?'<button data-review>'+(course.review?'Изменить отзыв':'Оставить отзыв')+'</button>':'')+'</section>';
}
function patientsPage(){
 const admin=state.user.role==='ADMIN';
 $('#content').innerHTML=heading('Пациенты',admin?'Все пациенты, включая тех, кому ещё не назначен специалист.':'Карточки пациентов, закреплённых за вами.','<button class="primary" id="new-patient">+ Добавить пациента</button>')+
 (state.patients.length?'<div class="cards">'+state.patients.map(p=>'<section class="card"><h3>'+esc(p.fullName)+'</h3><p class="muted">'+esc(p.rehabilitationReason||'Причина не указана')+'</p><small>Дата рождения: '+fmt(p.birthDate)+'<br>Специалист: '+esc(p.specialistName||'не назначен')+'</small><p style="margin-top:14px">'+esc(p.notes||'')+'</p>'+courseReview(p.finishedCourse)+'<div class="actions"><button data-patient="'+p.id+'">Открыть планы →</button>'+
 (admin?'<button data-assign="'+p.id+'">Назначить специалиста</button><button class="danger" data-delete-patient="'+p.id+'">Удалить</button>':'')+'</div></section>').join('')+'</div>':empty('Пациентов пока нет'));
 $('#new-patient').onclick=patientForm;
 bind('[data-patient]',async b=>{state.patientId=Number(b.dataset.patient);state.planId=null;state.tab='plans';await loadPlans();render()});
 bind('[data-assign]',async b=>{
  const p=state.patients.find(p=>p.id===Number(b.dataset.assign)),specialists=await api('/specialists');
  modal('Специалист для пациента','<p>'+esc(p.fullName)+'</p>'+select('Специалист','specialistId',[['','Не назначен'],...specialists.map(s=>[s.id,s.fullName])],p.specialistId??''),
   async data=>{await api('/patients/'+p.id+'/specialist','PATCH',{specialistId:data.specialistId?Number(data.specialistId):null});await start();toast('Назначение обновлено');});
 });
 bind('[data-delete-patient]',b=>{
  const p=state.patients.find(p=>p.id===Number(b.dataset.deletePatient));
  confirmAction('Удалить пациента?',p.fullName+'. Планы и записи дневника будут удалены, вход станет недоступен.',
   async()=>{await api('/patients/'+p.id,'DELETE');await start();toast('Пациент удалён');});
 });
}
async function specialistsPage(){
 const content=$('#content');
 content.innerHTML=heading('Специалисты','Создавайте учётные записи специалистов и управляйте их доступом.','<button class="primary" id="new-specialist">+ Добавить специалиста</button>')+'<div id="specialist-list"><p>Загрузка…</p></div>';
 $('#new-specialist').onclick=()=>modal('Новый специалист',credentialsFields(),async data=>{
  checkCredentials(data);await api('/specialists','POST',data);await specialistsPage();toast('Специалист добавлен');
 });
 try{
  const specialists=await api('/specialists'),list=content.querySelector('#specialist-list');
  if(!content.isConnected||!list)return;
  list.innerHTML=specialists.length?'<div class="cards">'+specialists.map(s=>'<section class="card"><h3>'+esc(s.fullName)+'</h3><p class="muted">Логин: '+esc(s.username)+'<br>Пациентов: '+state.patients.filter(p=>p.specialistId===s.id).length+'</p><button class="danger" data-delete-specialist="'+s.id+'">Удалить</button></section>').join('')+'</div>':empty('Специалистов пока нет');
  bind('[data-delete-specialist]',b=>{
   const s=specialists.find(s=>s.id===Number(b.dataset.deleteSpecialist));
   confirmAction('Удалить специалиста?',s.fullName+'. Вход станет недоступен. Пациенты, их планы и записи сохранятся, но останутся без этого специалиста.',
    async()=>{await api('/specialists/'+s.id,'DELETE');await start();toast('Специалист удалён');});
  });
 }catch(e){const list=content.querySelector('#specialist-list');if(list)list.innerHTML=empty(e.message);}
}
function exercisesPage(){
 $('#content').innerHTML=heading('Упражнения','Справочник для составления индивидуальных планов.','<button class="primary" id="new-exercise">+ Добавить упражнение</button>')+'<div class="cards">'+state.exercises.map(e=>'<section class="card"><span class="badge">'+units[e.unit]+'</span><h3 style="margin-top:14px">'+esc(e.title)+'</h3><p class="muted">'+esc(e.instructions)+'</p></section>').join('')+'</div>';$('#new-exercise').onclick=exerciseForm;
}
async function auditPage(page=0){
 $('#content').innerHTML=heading('Журнал действий','Кто и когда изменял данные. Текст дневника и пароли в журнал не записываются.')+'<p>Загрузка…</p>';
 try{const p=await api('/audit?page='+page);if(state.tab!=='audit')return;$('#content').innerHTML=heading('Журнал действий','Кто и когда изменял данные. Текст дневника и пароли в журнал не записываются.')+
 '<section class="card"><div class="table-wrap"><table class="entries"><thead><tr><th>Время</th><th>Пользователь</th><th>Действие</th><th>Объект</th></tr></thead><tbody>'+
 p.content.map(e=>'<tr><td>'+esc(new Date(e.occurredAt).toLocaleString('ru-RU'))+'</td><td>'+esc(e.actor)+'</td><td>'+esc(e.action)+'</td><td>'+esc(e.entityType)+' №'+e.entityId+'</td></tr>').join('')+'</tbody></table></div><div class="actions" style="margin-top:15px"><button id="prev" '+(p.first?'disabled':'')+'>Назад</button><span>Страница '+(page+1)+' · записей '+p.totalElements+'</span><button id="next" '+(p.last?'disabled':'')+'>Далее</button></div></section>';
 $('#prev').onclick=()=>auditPage(page-1);$('#next').onclick=()=>auditPage(page+1);
 }catch(e){if($('#content'))$('#content').innerHTML=empty(e.message);}
}
const eyeIcon='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6Z"/><circle cx="12" cy="12" r="2.5"/><path class="eye-slash" d="m4 20 16-16"/></svg>';
function field(label,name,type='text',value='',attrs=''){
 const input='<input id="f-'+name+'" name="'+name+'" type="'+type+'" value="'+esc(value)+'" '+attrs+'>';
 return '<div class="field"><label for="f-'+name+'">'+label+'</label>'+
 (type==='password'?'<div class="password-field">'+input+'<button type="button" class="password-toggle" data-toggle-password aria-controls="f-'+name+'" aria-label="Показать пароль" aria-pressed="false">'+eyeIcon+'</button></div>':input)+'</div>';
}
function area(label,name,value='',attrs=''){return '<div class="field"><label for="f-'+name+'">'+label+'</label><textarea id="f-'+name+'" name="'+name+'" '+attrs+'>'+esc(value)+'</textarea></div>';}
function select(label,name,options,value){return '<div class="field"><label for="f-'+name+'">'+label+'</label><select id="f-'+name+'" name="'+name+'">'+options.map(([v,t])=>'<option value="'+esc(v)+'" '+(String(v)===String(value)?'selected':'')+'>'+esc(t)+'</option>').join('')+'</select></div>';}
function modal(title,fields,onSubmit,label='Сохранить'){
 const d=$('#modal');$('#modal-body').innerHTML='<div class="modal-head"><h2>'+esc(title)+'</h2><button class="close" type="button" aria-label="Закрыть">×</button></div><form id="edit-form">'+fields+'<p class="error" role="alert" id="form-error"></p><div class="modal-actions"><button type="button" class="cancel">Отмена</button><button class="primary" type="submit">'+label+'</button></div></form>';
 d.querySelectorAll('[data-toggle-password]').forEach(button=>button.onclick=()=>{
  const input=document.getElementById(button.getAttribute('aria-controls')),show=input.type==='password';
  input.type=show?'text':'password';
  button.setAttribute('aria-pressed',String(show));
  button.setAttribute('aria-label',show?'Скрыть пароль':'Показать пароль');
 });
 if(!d.open)d.showModal();d.querySelector('.close').onclick=()=>d.close();d.querySelector('.cancel').onclick=()=>d.close();
 $('#edit-form').onsubmit=async e=>{e.preventDefault();const b=e.submitter;b.disabled=true;$('#form-error').textContent='';try{await onSubmit(Object.fromEntries(new FormData(e.target)));if($('#edit-form')===e.target)d.close();}catch(err){if($('#edit-form')===e.target)$('#form-error').textContent=err.message;}finally{b.disabled=false;}};
}
function confirmAction(title,text,action){modal(title,'<p>'+esc(text)+'</p>',action,'Подтвердить');}
function planForm(){
 modal('Новый план',field('Название','title','text','','required maxlength="150"')+area('Цель плана','goal','','required maxlength="1000"')+'<div class="row">'+field('Начало','startsOn','date',state.today,'required')+field('Окончание','endsOn','date',state.today,'required')+'</div><p class="form-hint">Сначала создаётся черновик. Добавьте упражнения, затем активируйте план.</p>',
 async data=>{const p=await api('/patients/'+state.patientId+'/plans','POST',data);state.planId=p.id;await loadPlans();render();toast('Черновик создан');});
}
function assignmentForm(){
 const used=new Set(state.detail.assignments.map(a=>a.title));const available=state.exercises.filter(e=>!used.has(e.title));
 if(!available.length){toast('Добавьте новое упражнение в справочник');return;}
 modal('Назначить упражнение',select('Упражнение','exerciseId',available.map(e=>[e.id,e.title+' · '+units[e.unit]]))+field('Целевая нагрузка в день','targetAmount','number','10','required min="0.01" max="100000" step="0.01"')+'<p class="form-hint">Упражнение назначается на каждый день периода плана.</p>',
 async data=>{await api('/plans/'+state.planId+'/assignments','POST',{exerciseId:Number(data.exerciseId),targetAmount:Number(data.targetAmount)});await loadDetail();render();toast('Упражнение добавлено');});
}
function entryForm(assignmentId=null,entry=null){
 if(!state.detail)return;const p=state.detail.plan;const max=state.today>p.endsOn?p.endsOn:state.today;
 if(max<p.startsOn){toast('План ещё не начался');return;}
 modal(entry?'Изменить запись':'Записать занятие',select('Упражнение','assignmentId',state.detail.assignments.map(a=>[a.id,a.title+' · цель '+num(a.targetAmount)+' '+units[a.unit]]),entry?.assignmentId||assignmentId)+
 field('Дата занятия','performedOn','date',entry?.performedOn||max,'required min="'+p.startsOn+'" max="'+max+'"')+
 select('Результат','status',[['DONE','Выполнено'],['SKIPPED','Пропущено']],entry?.status||'DONE')+
 field('Факт в единицах упражнения','actualAmount','number',entry?.actualAmount??'','required min="0" max="100000" step="0.01"')+
 '<div class="row">'+field('Боль от 0 до 10','pain','number',entry?.pain??0,'required min="0" max="10" step="1"')+field('Самочувствие от 1 до 10','wellbeing','number',entry?.wellbeing??7,'required min="1" max="10" step="1"')+'</div>'+
 area('Комментарий или причина пропуска','comment',entry?.comment||'','maxlength="2000"')+'<p class="form-hint">При пропуске: 0 нагрузки и причина. После изменения записи прежний ответ специалиста сбрасывается.</p>',
 async data=>{const body={...data,assignmentId:Number(data.assignmentId),actualAmount:Number(data.actualAmount),pain:Number(data.pain),wellbeing:Number(data.wellbeing),version:entry?.version??null};await api('/entries'+(entry?'/'+entry.id:''),entry?'PUT':'POST',body);await loadDetail();render();toast('Запись сохранена');});
 if(entry){$('#f-assignmentId').disabled=true;$('#f-performedOn').readOnly=true;const h=document.createElement('input');h.type='hidden';h.name='assignmentId';h.value=entry.assignmentId;$('#edit-form').append(h);}
 $('#f-status').onchange=e=>{if(e.target.value==='SKIPPED')$('#f-actualAmount').value=0;};
}
function feedbackForm(entry){
 modal('Ответ специалиста','<p><strong>'+esc(entry.exerciseTitle)+'</strong> · '+fmt(entry.performedOn)+'</p><p>'+esc(entry.comment||'Без комментария')+'</p>'+area('Ваш ответ','text',entry.feedback||'','required maxlength="2000"'),
 async data=>{await api('/entries/'+entry.id+'/feedback','PUT',{text:data.text,version:entry.version});await loadDetail();render();toast('Ответ сохранён');});
}
async function patientForm(){
 try{const specialists=await api('/specialists');if(!specialists.length)throw Error('Сначала добавьте специалиста.');modal('Добавить пациента',field('ФИО (фамилия, имя, отчество)','fullName','text','','required maxlength="150"')+field('Дата рождения','birthDate','date','','required')+field('Причина реабилитации','rehabilitationReason','text','','required maxlength="200"')+
 select('Специалист','specialistId',specialists.filter(s=>state.user.role==='ADMIN'||s.id===state.user.id).map(s=>[s.id,s.fullName]),state.user.id)+
 '<div class="row">'+field('Email','username','email','','required maxlength="254" autocomplete="email"')+field('Пароль','password','password','','required minlength="10" maxlength="72" autocomplete="new-password"')+'</div>'+area('Примечание','notes','','maxlength="2000"'),
 async data=>{const p=await api('/patients','POST',{...data,specialistId:Number(data.specialistId)});state.patientId=p.id;state.planId=null;state.patients=await api('/patients');await loadPlans();render();toast('Пациент создан');});}catch(e){toast(e.message)}
}
function exerciseForm(){
 modal('Новое упражнение',field('Название','title','text','','required maxlength="120"')+select('Единица нагрузки','unit',[['REPETITIONS','Повторения'],['MINUTES','Минуты'],['METERS','Метры']])+area('Описание и условия выполнения','instructions','','required maxlength="2000"'),
 async data=>{await api('/exercises','POST',data);state.exercises=await api('/exercises');render();toast('Упражнение создано');});
}
let chatKey=null;
let threadRequest=null;
const keyOf=t=>t.patientId+'/'+t.channel;
function refreshThreads(){
 if(!state.user||!$('#chat-badge')||document.hidden)return Promise.resolve(null);
 if(threadRequest)return threadRequest;
 const user=state.user;
 threadRequest=api('/chat/threads').then(list=>{
  if(state.user!==user)return null;
  const badge=$('#chat-badge'),total=list.reduce((n,t)=>n+t.unread,0);
  if(badge){badge.hidden=!total;badge.textContent=total>99?'99+':total;badge.setAttribute('aria-label',total+' непрочитанных сообщений');}
  return list;
 }).catch(()=>null).finally(()=>{threadRequest=null;});
 return threadRequest;
}
function chatPage(){
 const content=$('#content');
 content.innerHTML=heading('Сообщения',state.user.role==='PATIENT'?'Напишите назначенному специалисту или в техподдержку.':state.user.role==='ADMIN'?'Обращения пациентов в техподдержку.':'Переписка с закреплёнными пациентами.')+
  '<div class="chat-layout"><div class="card chat-list" id="chat-list"><p>Загрузка диалогов…</p></div><section class="card chat-panel" id="chat-panel">'+empty('Выберите диалог')+'</section></div>';
 const panel=content.querySelector('#chat-panel'),list=content.querySelector('#chat-list');
 let selection=null,updating=false,threadsStamp='';
 function paintThreads(threads){
  const stamp=JSON.stringify([threads,chatKey]);
  if(stamp===threadsStamp)return;
  threadsStamp=stamp;
  list.innerHTML=threads.length?threads.map(t=>{
   const label=state.user.role==='PATIENT'&&t.channel==='DOCTOR'?'Лечащий специалист':t.channel==='SUPPORT'?'Техподдержка':'Диалог с пациентом';
   return '<button class="chat-thread'+(chatKey===keyOf(t)?' selected':'')+'" data-thread="'+keyOf(t)+'"><span><strong>'+esc(t.title)+'</strong><small>'+label+'</small></span>'+
    (t.unread?'<span class="unread-dot" aria-label="'+t.unread+' непрочитанных">'+t.unread+'</span>':'')+'</button>';
  }).join(''):empty('Диалогов пока нет','Здесь появятся доступные переписки.');
  list.querySelectorAll('[data-thread]').forEach(b=>b.onclick=()=>{
   chatKey=b.dataset.thread;open(threads.find(t=>keyOf(t)===chatKey));paintThreads(threads);
  });
 }
 function open(thread){
  if(!thread){selection=null;panel.innerHTML=empty('Выберите диалог');return;}
  const chosen=selection={key:keyOf(thread),rows:new Map(),hasOlder:false,expanded:false,stamp:''};
  panel.innerHTML='<h2>'+esc(thread.title)+'</h2><button id="chat-older" type="button" hidden>Загрузить более ранние</button>'+
   '<div class="chat-history" id="chat-history" aria-label="История сообщений"></div>'+
   '<form id="chat-send">'+area('Сообщение','chatText','','required maxlength="2000"')+'<button class="primary" type="submit">Отправить</button></form>';
  panel.querySelector('#chat-older').onclick=()=>void load(chosen,true).catch(e=>toast(e.message));
  panel.querySelector('#chat-send').onsubmit=async e=>{
   e.preventDefault();const field=e.target.elements.chatText,button=e.submitter,text=field.value.trim();
   if(!text)return;
   button.disabled=true;
   try{await api('/chat/'+chosen.key,'POST',{text});field.value='';await load(chosen,false,true);}
   catch(err){toast(err.message);}finally{button.disabled=false;}
  };
  void load(chosen,false,true).catch(e=>{if(selection===chosen)toast(e.message);});
 }
 async function load(chosen,older=false,bottom=false){
  if(!content.isConnected||selection!==chosen||chosen.loading)return;
  chosen.loading=true;
  try{
   const before=older?Math.min(...chosen.rows.keys()):null;
   const result=await api('/chat/'+chosen.key+(older?'?before='+before:''));
   if(!content.isConnected||selection!==chosen)return;
   if(older||!chosen.expanded)chosen.hasOlder=result.hasOlder;
   if(older)chosen.expanded=true;
   for(const msg of result.messages)chosen.rows.set(msg.id,msg);
   const rows=[...chosen.rows.values()].sort((a,b)=>a.id-b.id);
   const history=panel.querySelector('#chat-history'),oldHeight=history.scrollHeight;
   const nearBottom=oldHeight-history.scrollTop-history.clientHeight<50;
   const stamp=JSON.stringify(rows);
   if(stamp!==chosen.stamp){
    chosen.stamp=stamp;
    history.innerHTML=rows.length?rows.map(m=>'<article class="chat-message'+(m.mine?' mine':'')+'"><small>'+esc(m.senderName)+' · '+esc(new Date(m.sentAt).toLocaleString('ru-RU'))+'</small><p>'+esc(m.body)+'</p></article>').join(''):empty('Сообщений пока нет','Начните диалог с первого сообщения.');
    if(older)history.scrollTop+=history.scrollHeight-oldHeight;
    else if(bottom||nearBottom)history.scrollTop=history.scrollHeight;
   }
   panel.querySelector('#chat-older').hidden=!chosen.hasOlder;
   const unread=result.messages.filter(m=>m.unread).map(m=>m.id);
   if(unread.length&&!document.hidden&&content.isConnected&&selection===chosen){
    await api('/chat/'+chosen.key+'/read','PATCH',{ids:unread});
    const threads=await refreshThreads();if(threads&&content.isConnected)paintThreads(threads);
   }
  }finally{chosen.loading=false;}
 }
 async function refresh(){
  if(updating)return;
  updating=true;
  try{
   const threads=await refreshThreads();
   if(!threads||!content.isConnected)return;
   if(selection&&!threads.some(t=>keyOf(t)===selection.key)){chatKey=null;open(null);}
   const saved=threads.find(t=>keyOf(t)===chatKey);
   if(!selection&&saved)open(saved);
   paintThreads(threads);
   if(selection)await load(selection);
  }catch(e){if(content.isConnected)toast(e.message);}
  finally{updating=false;}
 }
 content.refreshChat=refresh;
 void refresh();
}
function pollChat(){
 if(document.hidden||!state.user)return;
 const content=$('#content');
 if(content?.refreshChat)void content.refreshChat();
 else void refreshThreads();
}
setInterval(pollChat,5000);
document.addEventListener('visibilitychange',pollChat);
async function route(){
 try{
  if(location.hash==='#cabinet'){
   const r=await fetch('/api/auth/me');
   if(r.status===401){state.user=null;showHome();login();return;}
   if(!r.ok)throw Error('Не удалось открыть кабинет. Войдите снова.');
   await start(await r.json());
   if(location.hash==='#cabinet')window.scrollTo(0,0);
  }else{
   if(!$('.landing'))home();
   const id=location.hash.slice(1);
   if(id)document.getElementById(id)?.scrollIntoView();
  }
 }catch(e){showHome();toast(e.message);}
}
window.addEventListener('hashchange',route);
(async()=>{
 if(location.hash==='#cabinet'){await route();return;}
 home();
 try{const r=await fetch('/api/auth/me');if(r.ok){state.user=(await r.json()).user;if($('.landing')){home();const id=location.hash.slice(1);if(id)document.getElementById(id)?.scrollIntoView();}}}catch{}
})();
