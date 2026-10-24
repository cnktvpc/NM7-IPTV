const SOURCE = {
  home: {rss: ["https://thethao247.vn/trang-chu.rss"], page:"https://thethao247.vn/"},
  sports: {rss:["https://thethao247.vn/the-thao-c5.rss","https://thethao247.vn/the-thao-24h.rss"],page:"https://thethao247.vn/the-thao-c5/"},
  football: {rss:["https://thethao247.vn/bong-da.rss","https://thethao247.vn/bong-da-quoc-te-c2.rss"],page:"https://thethao247.vn/bong-da-quoc-te-c2/"},
  vietnam: {rss:["https://thethao247.vn/bong-da-viet-nam-c1.rss"],page:"https://thethao247.vn/bong-da-viet-nam-c1/"},
  vleague: {rss:["https://thethao247.vn/v-league-c15.rss"],page:"https://thethao247.vn/v-league-c15/"},
  national: {rss:["https://thethao247.vn/tuyen-quoc-gia-vn-c19.rss"],page:"https://thethao247.vn/bong-da-viet-nam-c1/"},
  international: {rss:["https://thethao247.vn/bong-da-quoc-te-c2.rss"],page:"https://thethao247.vn/bong-da-quoc-te-c2/"},
  epl: {rss:["https://thethao247.vn/ngoai-hang-anh-c8.rss"],page:"https://thethao247.vn/ngoai-hang-anh-c8/"},
  worldcup: {rss:["https://thethao247.vn/world-cup.rss"],page:"https://thethao247.vn/world-cup"},
  laliga: {rss:["https://thethao247.vn/bong-da-tay-ban-nha-la-liga-c9.rss"],page:"https://thethao247.vn/bong-da-tay-ban-nha-la-liga-c9/"},
  ucl: {rss:["https://thethao247.vn/cup-c1-c13.rss"],page:"https://thethao247.vn/cup-c1-c13/"},
  uel: {rss:["https://thethao247.vn/cup-c2-europa-league-c75.rss"],page:"https://thethao247.vn/cup-c2-europa-league-c75/"},
  bundesliga: {rss:["https://thethao247.vn/bong-da-duc-c11.rss"],page:"https://thethao247.vn/bong-da-duc-c11/"},
  transfer: {rss:["https://thethao247.vn/chuyen-nhuong.rss"],page:"https://thethao247.vn/chuyen-nhuong/"},
  prediction: {rss:["https://thethao247.vn/nhan-dinh-bong-da.rss","https://thethao247.vn/nhan-dinh.rss"],page:"https://thethao247.vn/nhan-dinh-bong-da/"},
  tennis: {rss:["https://thethao247.vn/quan-vot-tennis-c4.rss"],page:"https://thethao247.vn/quan-vot-tennis-c4/"},
  golf: {rss:["https://thethao247.vn/golf-c42.rss"],page:"https://thethao247.vn/golf-c42/"},
  basketball: {rss:["https://thethao247.vn/bong-ro-c43.rss"],page:"https://thethao247.vn/bong-ro-c43/"},
  badminton: {rss:["https://thethao247.vn/cau-long-c44.rss","https://thethao247.vn/cau-long.rss"],page:"https://thethao247.vn/cau-long-c44/"},
  volleyball: {rss:["https://thethao247.vn/bong-chuyen-c45.rss"],page:"https://thethao247.vn/bong-chuyen-c45/"},
  martial: {rss:["https://thethao247.vn/vo-thuat.rss"],page:"https://thethao247.vn/vo-thuat-c228/"}
};
const MIRROR = "https://rssamplifier.com/thethao247-vn-7.rss";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NM7NewsReader/1.0";
const CORS = {"Access-Control-Allow-Origin":"*","Access-Control-Allow-Methods":"GET, HEAD, OPTIONS","Access-Control-Allow-Headers":"Content-Type, Accept","Vary":"Origin"};
function response(body,status,extra){return new Response(body,{status,headers:{...CORS,...(extra||{})}});}
function validFeed(text){return /<(?:rss|feed|rdf:RDF)\b/i.test(text)&&/<(?:item|entry)\b/i.test(text);}
async function fetchText(url,timeout=5200){
 const abort=new AbortController(); const timer=setTimeout(()=>abort.abort(),timeout);
 try{
  const r=await fetch(url,{redirect:"follow",signal:abort.signal,headers:{"User-Agent":UA,"Accept":"application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*","Accept-Language":"vi-VN,vi;q=0.9,en-US;q=0.7,en;q=0.5","Referer":"https://thethao247.vn/rss.html","Cache-Control":"no-cache"}});
  if(!r.ok)return {ok:false,status:r.status,text:""};
  const text=await r.text(); if(!text||text.length>5*1024*1024)return {ok:false,status:502,text:""};
  return {ok:true,status:r.status,text};
 }catch(e){return {ok:false,status:0,text:""};}finally{clearTimeout(timer);}
}
function decodeHTML(s){return String(s||"").replace(/&nbsp;|&#160;/gi," ").replace(/&amp;/gi,"&").replace(/&quot;/gi,'"').replace(/&#39;|&apos;/gi,"'").replace(/&lt;/gi,"<").replace(/&gt;/gi,">").replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(Number(n))).replace(/&#x([0-9a-f]+);/gi,(_,n)=>String.fromCodePoint(parseInt(n,16)));}
function escapeXML(s){return String(s||"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&apos;");}
function htmlToRSS(html,base,category){
 const rx=/<a\b[^>]*href\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a\s*>/gi; const unique=new Map(); let m;
 while((m=rx.exec(html))!==null&&unique.size<70){
  let url;try{url=new URL(decodeHTML(m[2]),base);}catch{continue;}
  if(!/^https?:$/.test(url.protocol)||!/(^|\.)thethao247\.vn$/i.test(url.hostname))continue;
  const path=url.pathname.toLowerCase(); if(path==="/"||/\/(rss|tag|tim-kiem|search|gioi-thieu)\b/.test(path))continue;
  if(!/\.html?$|\/?$/.test(path)||path.length<8)continue;
  const title=decodeHTML(m[3].replace(/<script\b[\s\S]*?<\/script>/gi," ").replace(/<style\b[\s\S]*?<\/style>/gi," ").replace(/<[^>]+>/g," ").replace(/\s+/g," ")).trim();
  if(title.length<14||title.length>220||/^(xem thêm|đọc tiếp|xem tất cả|lịch thi đấu|kết quả|bảng xếp hạng)$/i.test(title))continue;
  const key=url.hostname+url.pathname;if(!unique.has(key))unique.set(key,{url:url.toString(),title});
 }
 if(!unique.size)return "";
 const items=[...unique.values()].map(x=>'<item><title>'+escapeXML(x.title)+'</title><link>'+escapeXML(x.url)+'</link><guid isPermaLink="true">'+escapeXML(x.url)+'</guid><description>'+escapeXML("Tin "+category+" từ Thể Thao 247")+'</description></item>').join("");
 return '<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>Thể Thao 247 - '+escapeXML(category)+'</title><link>'+escapeXML(base)+'</link><description>RSS dự phòng do NM7 Reader chuẩn hóa</description>'+items+'</channel></rss>';
}
function addHeaders(body,category,upstream,cacheStatus="MISS"){return response(body,200,{"Content-Type":"application/rss+xml; charset=utf-8","Cache-Control":"public, max-age=300, s-maxage=300","X-NM7-Source":"thethao247","X-NM7-Category":category,"X-NM7-Upstream":upstream,"X-NM7-Cache":cacheStatus});}
export default {
 async fetch(request,env,ctx){
  const u=new URL(request.url);
  if(request.method==="OPTIONS")return response("",204);
  if(request.method!=="GET"&&request.method!=="HEAD")return response("method not allowed",405,{"Allow":"GET, HEAD, OPTIONS"});
  if(u.pathname==="/"||u.pathname==="/health")return response(JSON.stringify({ok:true,worker:"nm7-news-reader-api",version:"1.0.0",source:"thethao247",cacheSeconds:300,categories:Object.keys(SOURCE)}),200,{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"});
  const parts=u.pathname.split("/").filter(Boolean);
  if(parts.length!==2||parts[0]!=="rss")return response("not found",404,{"Content-Type":"text/plain; charset=utf-8"});
  const category=parts[1].toLowerCase();const cfg=SOURCE[category];
  if(!cfg)return response(JSON.stringify({ok:false,error:"unknown category",allowed:Object.keys(SOURCE)}),400,{"Content-Type":"application/json; charset=utf-8"});
  const cacheKey=new Request(new URL("/__nm7_cache/thethao247/"+category,u.origin).toString(),{method:"GET"});
  const cached=await caches.default.match(cacheKey);
  if(cached){const h=new Headers(cached.headers);h.set("X-NM7-Cache","HIT");return new Response(request.method==="HEAD"?null:cached.body,{status:cached.status,headers:h});}
  const jobs=[...cfg.rss.map(url=>fetchText(url,5200).then(r=>({kind:"rss",url,...r}))),fetchText(cfg.page,5200).then(r=>({kind:"html",url:cfg.page,...r}))];
  const results=await Promise.all(jobs);let chosen=null;
  for(const r of results){if(r.ok&&r.kind==="rss"&&validFeed(r.text)){chosen={body:r.text,upstream:r.url};break;}}
  if(!chosen){const html=results.find(r=>r.ok&&r.kind==="html");if(html){const body=htmlToRSS(html.text,cfg.page,category);if(body)chosen={body,upstream:"html:"+html.url};}}
  if(!chosen&&["home","football","vietnam","vleague","national","international"].includes(category)){const mirror=await fetchText(MIRROR,5200);if(mirror.ok&&validFeed(mirror.text))chosen={body:mirror.text,upstream:"rssamplifier-cache"};}
  if(!chosen){const statuses=results.map(r=>r.kind+":"+r.status).join(",");return response(JSON.stringify({ok:false,error:"upstream unavailable",category,attempts:statuses}),502,{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store","Retry-After":"30"});}
  const result=addHeaders(chosen.body,category,chosen.upstream);ctx.waitUntil(caches.default.put(cacheKey,result.clone()).catch(()=>{}));if(request.method==="HEAD")return new Response(null,{status:200,headers:result.headers});return result;
 }
};