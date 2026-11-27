/** PhotoZest Web: photos never leave the browser; downloaded model weights do not contain user files. */
export type Category = 'Landscapes' | 'Group photos' | 'Selfies' | 'Candids' | 'Food' |
  'Portraits' | 'Payment receipts' | 'Screenshots' | 'Needs review';
export type Payment = { provider: string; amount: number | null; payee: string | null; status: 'success' | 'failed' | 'pending' | 'unknown'; direction: 'sent' | 'received' | 'unknown' };
export type RecordPhoto = {
  id: string; file: File; url: string; width: number; height: number; category: Category;
  confidence: number; reason: string; tags: string[]; city: string | null; captured: string | null;
  timeOfDay: string | null; sharpness: number; brightness: number; duplicateOf?: string;
  payment?: Payment | null; labels: string[];
};
export type VisionEvidence = { labels: {name: string; score: number}[]; persons: {bbox: [number,number,number,number];score:number}[] };
export type Quality = { sharpness: number; brightness: number; contrast: number; highlights: number; shadows: number; width: number; height: number; hash: bigint };

const foodWords = new Set(['food','dish','meal','cuisine','pizza','cake','bread','salad','sushi','pasta','pastry','burger','sandwich','fruit','vegetable','dining']);
const scenicWords = new Set(['landscape','mountain','forest','beach','lake','river','waterfall','nature','sea','sunrise','sunset','valley','desert','ocean','coast','seashore']);
const score = (evidence: VisionEvidence | null, words: Set<string>) => {
  const scores = (evidence?.labels ?? []).filter(p => p.name.toLowerCase().replace(/[^a-z0-9]+/g,' ').split(' ').some(t => words.has(t)))
    .map(p => p.score).sort((a,b)=> b-a);
  return Math.min(1,(scores[0] ?? 0) + (scores[1] ?? 0)*0.08);
};
export function classifyPhoto(q: Quality, evidence: VisionEvidence | null): {category: Category; confidence:number;reason:string;tags:string[]} {
  const food = score(evidence, foodWords), scenic = score(evidence, scenicWords);
  const faces = (evidence?.persons ?? []).filter(p=>p.score>=0.55);
  const blurred = q.sharpness < (q.brightness < 0.16 ? 0.15 : 0.19);
  const wellExposed = q.brightness>=0.19 && q.brightness<=0.88 && q.contrast>=0.10 && q.highlights<=0.22 && q.shadows<=0.36;
  const highResolution = Math.min(q.width,q.height) >= 1000;
  const tags:string[] = [];
  if (blurred) tags.push('Possibly blurred');
  if (!blurred && q.sharpness>=0.43 && wellExposed && highResolution) tags.push('Social-ready');
  if (!blurred && q.sharpness>=0.51 && wellExposed && highResolution && scenic>=0.69) tags.push('Frame-worthy');
  if (faces.length>=2) return {category:'Group photos', confidence:0.84, reason:'Multiple people detected',tags};
  if (faces.length===1) {
    const [x,,w,h]=faces[0].bbox; const area=w*h/(q.width*q.height); const center=(x+w/2)/q.width;
    if (area >=0.23 && center>=0.26 && center<=0.74) return {category:'Selfies',confidence:0.53, reason:'Close centered person; verify selfie',tags};
    if (area<0.10 && (center<0.25 || center>0.75)) return {category:'Candids',confidence:0.44,reason:'Environmental subject; possible candid',tags};
    return {category:'Portraits',confidence:0.71,reason:'One person detected',tags};
  }
  if (food>=0.68 && scenic>=0.68 && Math.abs(food-scenic)<0.14) return {category:'Needs review',confidence:0.45,reason:'Conflicting scene signals',tags};
  if (food>=0.68 && food>scenic) return {category:'Food',confidence:food,reason:'Food-related labels',tags};
  if (scenic>=0.67 && scenic>=food) return {category:'Landscapes',confidence:scenic,reason:'Scenery-related labels',tags};
  return {category:'Needs review',confidence:0,reason:evidence?'Not enough reliable evidence':'Basic quality scan only',tags};
}

export const cities = [
  ['Mumbai',19.076,72.8777,25], ['Thane',19.2183,72.9781,13],['Navi Mumbai',19.033,73.0297,17],
  ['Pune',18.5204,73.8567,28],['Lonavala',18.7546,73.4062,10],['Alibaug',18.6414,72.8722,12],
  ['Nashik',19.9975,73.7898,20],['Hyderabad',17.385,78.4867,32],['Bengaluru',12.9716,77.5946,37],
  ['Delhi',28.6139,77.2090,35],['Jaipur',26.9124,75.7873,24],['Goa',15.4909,73.8278,12],
] as const;
export function cityFromGps(lat:number|null,lng:number|null):string|null {
  if(lat===null || lng===null || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180) return null;
  let best: {name:string;km:number}|null=null;
  for (const [name,placeLat,placeLng,radius] of cities) {
    const dy=(placeLat-lat)*Math.PI/180,dx=(placeLng-lng)*Math.PI/180;
    const a=Math.sin(dy/2)**2+Math.cos(lat*Math.PI/180)*Math.cos(placeLat*Math.PI/180)*Math.sin(dx/2)**2;
    const km=12742*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    if(km <= radius && (!best || km < best.km))best={name,km};
  }
  return best?.name??null;
}
export function timeOfDay(iso:string|null):string|null {
  const hour=iso?.match(/[T ](\d{2}):/)?.[1]; if(hour===undefined)return null;
  const h=Number(hour); if(!Number.isFinite(h) || h>23)return null;
  if(h>=4 && h<=6)return'Early morning'; if(h>=7&&h<=10)return'Morning';
  if(h>=11&&h<=15)return'Afternoon';if(h>=16&&h<=19)return'Evening';return'Night';
}
export function screenshotName(name:string):boolean {return /(screenshot|screen.?shot|screen.?capture|\.trashed.*screenshot)/i.test(name)}
export function parsePayment(text:string): Payment | null {
  const t=text.replace(/\s+/g,' ').trim();
  const provider= /phonepe/i.test(t)?'PhonePe':/gpay|google pay/i.test(t)?'Google Pay':/paytm/i.test(t)?'Paytm':/amazon pay/i.test(t)?'Amazon Pay':/\bbhim\b/i.test(t)?'BHIM':/\bupi\b/i.test(t)?'UPI':null;
  if(!provider)return null;
  const status:Payment['status'] = /failed|declined|unsuccessful|not completed/i.test(t)?'failed':/pending|processing|in progress/i.test(t)?'pending':/successful|successfully|completed|paid to|sent to|received from/i.test(t)?'success':'unknown';
  const direction:Payment['direction'] = /received from|money received|credited to/i.test(t)?'received':/paid to|sent to|money sent|debited from/i.test(t)?'sent':'unknown';
  const amountMatch=t.match(/(?:₹|\bINR\s*|\bRs\.?\s*)([\d,]+(?:\.\d{1,2})?)/i);
  const amount=amountMatch?Number(amountMatch[1].replaceAll(',','')):null;
  const payee=t.match(/(?:paid to|sent to|received from)\s+([a-z][a-z .'-]{1,45}?)(?=\s+(?:on|at|₹|rs\b|upi|transaction|reference|successful|completed)|$)/i)?.[1]?.trim() ?? null;
  return {provider,status,direction,amount:amount !== null && Number.isFinite(amount) && amount > 0 ? amount : null,payee};
}
export function likelyDuplicate(a:Quality,b:Quality):boolean {
  if(Math.abs(a.width/a.height-b.width/b.height)>0.06)return false;
  let x=a.hash^b.hash, bits=0;while(x){bits++;x&=x-1n;}
  return bits<=4;
}
function luma(data:Uint8ClampedArray,index:number):number{return (0.2126*data[index]+0.7152*data[index+1]+0.0722*data[index+2])/255;}
export function qualityFromImage(image:ImageData, fullWidth:number,fullHeight:number):Quality {
  const {data,width,height}=image; const gray=new Float64Array(width*height);
  let total=0,highlights=0,shadows=0;
  for(let i=0;i<gray.length;i++){const v=luma(data,i*4);gray[i]=v;total+=v;if(v>=0.97)highlights++;if(v<=0.035)shadows++;}
  const brightness=total/gray.length;
  let contrast=0,lapTotal=0,lapSquared=0,n=0;
  for(const v of gray)contrast+=(v-brightness)**2;
  for(let y=1;y<height-1;y++)for(let x=1;x<width-1;x++){
    const i=y*width+x,v=(4*gray[i]-gray[i-1]-gray[i+1]-gray[i-width]-gray[i+width])*255;
    lapTotal+=v;lapSquared+=v*v;n++;
  }
  const lapVar=Math.max(0,lapSquared/Math.max(n,1)-(lapTotal/Math.max(n,1))**2);
  // Edge energy normalized to Android's 96x96 image analysis grid.
  const sharpness=Math.min(1,Math.sqrt(lapVar)/56);
  let hash=0n;
  for(let y=0;y<8;y++)for(let x=0;x<8;x++){
    const lx=gray[Math.min(height-1,Math.floor((y+0.5)*height/8))*width+Math.min(width-1,Math.floor((x+0.5)*width/9))];
    const rx=gray[Math.min(height-1,Math.floor((y+0.5)*height/8))*width+Math.min(width-1,Math.floor((x+1.5)*width/9))];
    hash=(hash<<1n)|(lx>rx?1n:0n);
  }
  return {width:fullWidth,height:fullHeight,brightness,contrast:Math.sqrt(contrast/gray.length),sharpness,highlights:highlights/gray.length,shadows:shadows/gray.length,hash};
}
export type LoadedVision = { classify: (image: HTMLCanvasElement, k:number)=>Promise<{className:string;probability:number}[]>; detect:(image:HTMLCanvasElement,k:number,min:number)=>Promise<{class:string;score:number;bbox:[number,number,number,number]}[]> };
let promise: Promise<LoadedVision>|null=null;
export function loadVision():Promise<LoadedVision> {
  if(promise)return promise;
  promise=(async()=>{
    await import('@tensorflow/tfjs');
    const [{load:mobile},{load:coco}]=await Promise.all([import('@tensorflow-models/mobilenet'),import('@tensorflow-models/coco-ssd')]);
    const [m,c]=await Promise.all([mobile({version:2,alpha:1}),coco({base:'lite_mobilenet_v2'})]);
    return {classify:(image:HTMLCanvasElement,k:number)=>m.classify(image,k),detect:(image:HTMLCanvasElement,k:number,min:number)=>c.detect(image,k,min)};
  })().catch(error=>{promise=null;throw error});
  return promise;
}
export async function inspectCanvas(canvas:HTMLCanvasElement,model:LoadedVision):Promise<VisionEvidence> {
  const [classifications,objects]=await Promise.all([model.classify(canvas,5),model.detect(canvas,20,0.48)]);
  return {labels:[...classifications.map(p=>({name:p.className,score:p.probability})),...objects.map(p=>({name:p.class,score:p.score}))],persons:objects.filter(p=>p.class==='person'&&p.score>=0.55).map(p=>({bbox:p.bbox,score:p.score}))};
}
export async function readCaptureMetadata(file:File):Promise<{city:string|null;captured:string|null}> {
  try{
    const exifr=await import('exifr');
    const meta=await exifr.parse(file,{pick:['DateTimeOriginal','DateTimeDigitized','CreateDate','latitude','longitude']});
    const raw=meta?.DateTimeOriginal??meta?.DateTimeDigitized??meta?.CreateDate;
    const captured=raw instanceof Date && Number.isFinite(raw.getTime())?
      `${raw.getFullYear()}-${String(raw.getMonth()+1).padStart(2,'0')}-${String(raw.getDate()).padStart(2,'0')} ${String(raw.getHours()).padStart(2,'0')}:${String(raw.getMinutes()).padStart(2,'0')}`:null;
    const lat=typeof meta?.latitude==='number'?meta.latitude:null,lng=typeof meta?.longitude==='number'?meta.longitude:null;
    return {city:cityFromGps(lat,lng),captured};
  }catch{return {city:null,captured:null}}
}
let ocrWorkerPromise: Promise<{recognize:(canvas:HTMLCanvasElement)=>Promise<{data:{text:string}}> ;terminate:()=>Promise<unknown>}> | null=null;
export async function readScreenshotText(canvas:HTMLCanvasElement):Promise<string> {
  try{
    if(!ocrWorkerPromise)ocrWorkerPromise=import('tesseract.js').then(mod=>mod.createWorker('eng'));
    const worker=await ocrWorkerPromise;
    const {data}=await worker.recognize(canvas);return data.text;
  }catch{ocrWorkerPromise=null;return ''}
}
export async function releaseOcr():Promise<void> { const current=ocrWorkerPromise;ocrWorkerPromise=null;if(current)try{await (await current).terminate()}catch{} }
