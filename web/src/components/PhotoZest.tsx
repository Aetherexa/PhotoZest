import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {Album,ArrowLeft,Check,CheckCheck,ChevronRight,Folder,Image as ImageIcon,MapPin,MoreHorizontal,Search,Share2,ShieldCheck,SlidersHorizontal, Sparkles,Tag,Wallet, X} from 'lucide-react';
import {classifyPhoto,cityFromGps,inspectCanvas,likelyDuplicate,loadVision,parsePayment,qualityFromImage,readCaptureMetadata,readScreenshotText,releaseOcr,screenshotName,timeOfDay} from './engine';
import type {Category,Quality,RecordPhoto,LoadedVision} from './engine';
import './photozest.css';
import { photoZestFeatures } from '../featureFlags.generated';

const categories:Category[]=['Landscapes','Group photos','Selfies','Candids','Food','Portraits','Payment receipts','Screenshots','Needs review'];
const limit=250;
type Page='home'|'photos'|'collections';
const fileInputAccept='image/jpeg,image/png,image/webp,image/heic,image/heif,image/avif,image/bmp';
const isPhoto=(file:File)=>/^image\//.test(file.type)||/\.(jpe?g|png|webp|heic|heif|avif|bmp)$/i.test(file.name);
const unique=(xs:string[])=>Array.from(new Set(xs.filter(Boolean)));
const labelForDate=(date:string)=>{if(date==='Unknown date')return date;const [y,m]=date.split('-').map(Number);return new Date(y,m-1,1).toLocaleDateString('en-US',{month:'long',year:'numeric'});};
const luminanceTags=(q:Quality)=>[q.brightness<0.2?'Low light':'',q.highlights>0.24?'Bright highlights':'',q.shadows>0.28?'Dark shadows':'',q.width>q.height*1.15?'Landscape orientation':q.height>q.width*1.15?'Portrait orientation':'Square',Math.min(q.width,q.height)>=1600?'High resolution':''].filter(Boolean);
function groupByMonth(photos:RecordPhoto[]){const groups=new Map<string,RecordPhoto[]>();for(const p of photos){const key=p.captured?.slice(0,7)||'Unknown date';groups.set(key,[...(groups.get(key)||[]),p]);}return [...groups.entries()].sort((a,b)=>b[0].localeCompare(a[0]));}
function matchesSearch(p:RecordPhoto,query:string){const q=query.trim().toLowerCase();if(!q)return true;
  const fields=unique([p.file.name,p.category,p.city||'',p.captured||'',p.timeOfDay||'',...p.tags,...p.labels,p.payment?.provider||'',p.payment?.payee||'',p.payment?.direction||'',p.payment?.status||'']).join(' ').toLowerCase();
  const amountQuery=q.match(/(?:above|over|greater than|>)\s*(?:₹\s*)?([\d,.]+)/);if(amountQuery){const threshold=Number(amountQuery[1].replaceAll(',',''));if(!(p.payment?.amount&&p.payment.amount>threshold))return false;}
  const tokens=q.replace(/(?:above|over|greater than|>)\s*(?:₹\s*)?[\d,.]+/g,'').replace(/\b(show|find|my|me|photos|pictures|images|from|taken|of|the|in|on|during|only|all|please|and)\b/g,' ').split(/\s+/).filter(Boolean);
  return tokens.every(t=>fields.includes(t));
}

export default function PhotoZest(){
 const [page,setPage]=useState<Page>('home');const [photos,setPhotos]=useState<RecordPhoto[]>([]);
 const [loading,setLoading]=useState(false);const [processed,setProcessed]=useState(0);const [total,setTotal]=useState(0);const [status,setStatus]=useState('');
 const [search,setSearch]=useState('');const [category,setCategory]=useState<Category|'All'>('All');const [place,setPlace]=useState('All places');const [showFilters,setShowFilters]=useState(false);
 const [enhanced,setEnhanced]=useState(photoZestFeatures.enhancedRecognition);const [ocr,setOcr]=useState(false);
 const [selection,setSelection]=useState<Set<string>>(new Set());const [selectMode,setSelectMode]=useState(false);
 const [detail,setDetail]=useState<string|null>(null);const [collections,setCollections]=useState<Record<string,string[]>>({});
 const [newCollection,setNewCollection]=useState(false);const [collectionName,setCollectionName]=useState('');
 const [notice,setNotice]=useState('');const [preferredCategory,setPreferredCategory]=useState<Category|null>(null);
 const chooseFiles=useRef<HTMLInputElement>(null),chooseFolder=useRef<HTMLInputElement>(null),generation=useRef(0),urls=useRef<string[]>([]),metadataById=useRef(new Map<string,Quality>());
 const {countByCategory,cityCounts}=useMemo(()=>{
  const countByCategory=new Map<Category,number>(),cityCounts=new Map<string,number>();
  for(const p of photos){countByCategory.set(p.category,(countByCategory.get(p.category)||0)+1);if(p.city)cityCounts.set(p.city,(cityCounts.get(p.city)||0)+1);}
  return {countByCategory,cityCounts};
 },[photos]);
 const visible=useMemo(()=>photos.filter(p=>(category==='All'||p.category===category)&&(place==='All places'||p.city===place)&&matchesSearch(p,search)),[photos,category,place,search]);
 const selectedPhotos=visible.filter(p=>selection.has(p.id));const focused=photos.find(p=>p.id===detail)||null;
 useEffect(()=>()=>{generation.current++;urls.current.forEach(u=>URL.revokeObjectURL(u));void releaseOcr();},[]);
 const clearSession=()=>{generation.current++;urls.current.forEach(u=>URL.revokeObjectURL(u));urls.current=[];metadataById.current.clear();setPhotos([]);setCollections({});setSelection(new Set());setSelectMode(false);setCategory('All');setSearch('');setPlace('All places');setPreferredCategory(null);};
 const scan=async(input:FileList|null,kind:'gallery'|'folder')=>{
  if(!input)return;if(kind==='folder'&&!photoZestFeatures.folderImport){setNotice('Folder import is disabled in this release.');return;}const picked=Array.from(input).filter(isPhoto).slice(0,limit);if(!picked.length){setNotice('Choose JPG, PNG, WebP or another supported image format.');return;}
  clearSession();const run=++generation.current;setLoading(true);setProcessed(0);setTotal(picked.length);setStatus('Preparing on-device analysis…');setNotice('');
  let model:LoadedVision|null=null;
  if(enhanced&&photoZestFeatures.enhancedRecognition){try{model=await loadVision()}catch{if(run===generation.current)setNotice('Visual model unavailable. Using conservative quality and metadata analysis.');}}
  const output:RecordPhoto[]=[];
  for(let i=0;i<picked.length&&run===generation.current;i++){
   const file=picked[i];const url=URL.createObjectURL(file);urls.current.push(url);
   try{
    const image=new window.Image();image.src=url;await image.decode();
    const max=720,ratio=Math.min(1,max/Math.max(image.naturalWidth,image.naturalHeight));
    const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(image.naturalWidth*ratio));canvas.height=Math.max(1,Math.round(image.naturalHeight*ratio));
    const ctx=canvas.getContext('2d',{willReadFrequently:true});if(!ctx)throw Error('Canvas unavailable');ctx.drawImage(image,0,0,canvas.width,canvas.height);
    const qCanvas=document.createElement('canvas');qCanvas.width=96;qCanvas.height=96;const qualityCtx=qCanvas.getContext('2d',{willReadFrequently:true});if(!qualityCtx)throw Error('Quality canvas unavailable');qualityCtx.drawImage(image,0,0,96,96);
    const q=qualityFromImage(qualityCtx.getImageData(0,0,96,96),image.naturalWidth,image.naturalHeight);
    const metadata=await readCaptureMetadata(file);const screenshot=screenshotName(file.name);
    const evidence=(!screenshot&&model)?await inspectCanvas(canvas,model).catch(()=>null):null;
    const classification=classifyPhoto(q,evidence);
    let payment=null;let categoryNow:Category=screenshot?'Screenshots':classification.category;
    if(screenshot&&ocr&&photoZestFeatures.screenshotOcr){const text=await readScreenshotText(canvas);payment=parsePayment(text);if(payment)categoryNow='Payment receipts';}
    const duplicate=output.find(p=>p.category===categoryNow&&categoryNow!=='Needs review'&&categoryNow!=='Screenshots'&&categoryNow!=='Payment receipts'&&metadataById.current.has(p.id)&&likelyDuplicate(q,metadataById.current.get(p.id)!));
    const id=`${i}-${file.name}-${file.size}`;
    metadataById.current.set(id,q);
    const tags=unique([...classification.tags,...luminanceTags(q),...((evidence?.labels??[]).filter(x=>x.score>=0.65).slice(0,4).map(x=>x.name)),...(screenshot?['Screenshot',ocr?'OCR processed':'']:[]),...(payment?[payment.provider,payment.status,payment.direction]:[]),...(metadata.city?[metadata.city]:[]),...(metadata.captured?[timeOfDay(metadata.captured)||'']:[]),...(duplicate?['Possible duplicate']:[])]);
    output.push({id,file,url,width:q.width,height:q.height,category:categoryNow,confidence:payment?0.70:classification.confidence,reason:payment?'Payment data recognized from screenshot; verify before relying on it':screenshot?'Screenshot inferred from filename':classification.reason,tags,city:metadata.city,captured:metadata.captured,timeOfDay:timeOfDay(metadata.captured),sharpness:q.sharpness,brightness:q.brightness,duplicateOf:duplicate?.id,payment,labels:evidence?.labels.map(x=>x.name)??[]});
    setPhotos([...output]);
   }catch(error){if(error instanceof Error)setStatus(`${file.name}: ${error.message}. Skipping unsupported image.`)}
   if(run===generation.current){setProcessed(i+1);setStatus(`Analyzing ${i+1} of ${picked.length} images locally`)}
  }
  if(run===generation.current){setLoading(false);setPage('photos');setCategory(preferredCategory||'All');setStatus(`${output.length} photos indexed. Originals unchanged.`);}
  if(kind==='folder'&&chooseFolder.current)chooseFolder.current.value='';if(kind==='gallery'&&chooseFiles.current)chooseFiles.current.value='';
 };
 const toggle=(id:string)=>setSelection(prev=>{const next=new Set(prev);next.has(id)?next.delete(id):next.add(id);return next;});
 const share=async()=>{
  if(!photoZestFeatures.browserSharing){setNotice('Browser sharing is disabled in this release.');return;}
  const files=selectedPhotos.map(p=>p.file);if(!files.length)return;
  if(selectedPhotos.some(p=>p.payment)){if(!window.confirm('Payment screenshots may contain personal information. Share these original files?'))return;}
  if(!navigator.share||!navigator.canShare?.({files})){setNotice('This browser cannot share selected files. Open PhotoZest Android to share originals directly.');return;}
  try{await navigator.share({files,title:'Shared with PhotoZest'});}catch(e){if((e as Error).name!=='AbortError')setNotice('Sharing was not available in this browser.');}
 };
 const createCollection=()=>{
  if(!photoZestFeatures.smartCollections){setNotice('Custom smart collections are disabled in this release.');return;}
  const name=collectionName.trim().slice(0,60);if(!name||!selectedPhotos.length)return;
  setCollections(prev=>({...prev,[name]:unique([...(prev[name]||[]),...selectedPhotos.map(p=>p.id)])}));setNewCollection(false);setCollectionName('');setSelectMode(false);setSelection(new Set());setPage('collections');setNotice(`Created smart collection “${name}”. No files were copied or moved.`);
 };
 const buttonPickGallery=()=>chooseFiles.current?.click(),buttonPickFolder=()=>{if(photoZestFeatures.folderImport)chooseFolder.current?.click();};
 const leaveSelection=()=>{setSelectMode(false);setSelection(new Set());};
 const openCollection=(cat:Category)=>{setCategory(cat);setPlace('All places');setSearch('');setPage('photos');leaveSelection()};
 const categoryCounts=categories.filter(c=>(countByCategory.get(c)||0)>0);
 return <div className="pf-app">
  <input ref={chooseFiles} type="file" accept={fileInputAccept} multiple hidden onChange={e=>void scan(e.currentTarget.files,'gallery')}/>
  <input ref={chooseFolder} type="file" accept="image/*" multiple hidden {...{webkitdirectory:'',directory:''}} onChange={e=>void scan(e.currentTarget.files,'folder')}/>
  <div className="pf-shell">
   <div className="pf-header">
    <a className="pf-brand" href={import.meta.env.BASE_URL} onClick={e=>{e.preventDefault();setPage('home');leaveSelection()}}><span className="pf-logo">✦</span><span><b>PhotoZest</b><small>YOUR MEMORIES, REIMAGINED</small></span></a>
    <div className="pf-header-right"><span className="pf-local"><ShieldCheck size={15}/> On-device</span><button type="button" className="pf-icon-btn" aria-label="PhotoZest settings" onClick={()=>setNotice('Options depend on the platform release profile.') }><MoreHorizontal size={21}/></button></div>
   </div>
   {page==='home'&&<>
    <label className="pf-search" aria-label="Search your photos"><Search size={23}/><input placeholder="Search photos, places, payments or tags" value={search} onChange={e=>{setSearch(e.target.value);if(photos.length)setPage('photos');}} onFocus={()=>{if(photos.length)setPage('photos')}}/></label>
    <div className="pf-shortcuts"><button onClick={buttonPickGallery}><ImageIcon/><span>Gallery</span></button>{photoZestFeatures.folderImport&&<button onClick={buttonPickFolder}><Folder/><span>Folder</span></button>}<button onClick={()=>{if(photos.length){openCollection('Payment receipts')}else{setPreferredCategory('Payment receipts');buttonPickGallery()}}}><Wallet/><span>Payments</span></button></div>
    <section className="pf-hero"><div className="pf-hero-inner"><div><h1>Start with<br/>your photos</h1><p>Pick your photos or a folder, and let PhotoZest organize them for you.</p></div><img className="pf-collage" src={`${import.meta.env.BASE_URL}photozest-hero.webp`} alt="Overlapping landscape, beach and group photo previews"/></div>
     <div className="pf-hero-actions"><button className="pf-primary" onClick={buttonPickGallery}><ImageIcon size={20}/> Choose from Gallery</button>{photoZestFeatures.folderImport&&<button className="pf-secondary" onClick={buttonPickFolder}><Folder size={20}/> Choose Folder</button>}</div>
    </section>
    <div className="pf-section-title"><h2>Smart collections</h2><button onClick={()=>setPage('collections')}>See all <ChevronRight size={16}/></button></div>
    {categoryCounts.length?<div className="pf-collection-grid">{categoryCounts.slice(0,4).map(c=>{const cover=photos.find(p=>p.category===c);return <button key={c} className="pf-collection" onClick={()=>openCollection(c)}>{cover&&<img src={cover.url} alt=""/>}<span><strong>{c}</strong><small>{countByCategory.get(c)} photos</small></span><ChevronRight size={16}/></button>})}</div>:<div className="pf-empty-collection"><Sparkles size={20}/><div><b>Your collections will appear here</b><p>Choose photos to discover landscapes, groups, screenshots and more.</p></div></div>}
    {Object.keys(collections).length>0&&<p className="pf-muted">You also have {Object.keys(collections).length} custom smart collection(s) in this session.</p>}
    <div className="pf-preferences">{photoZestFeatures.enhancedRecognition&&<label><span>Enhanced scene recognition<small>Downloads AI model weights the first time; photos stay on this device.</small></span><input type="checkbox" checked={enhanced} onChange={e=>setEnhanced(e.target.checked)} disabled={loading}/></label>}{photoZestFeatures.screenshotOcr&&<label><span>Payment screenshot OCR<small>Optional; may download OCR language data on first use.</small></span><input type="checkbox" checked={ocr} onChange={e=>setOcr(e.target.checked)} disabled={loading}/></label>}</div>
   </>}
   {page==='photos'&&<>
    <div className="pf-page-title"><div><h1>Your photos</h1><p>{photos.length} originals indexed · stored only in this tab</p></div><button onClick={buttonPickGallery} aria-label="Add photos" className="pf-add">+ Add</button></div>
    <label className="pf-search"><Search size={20}/><input aria-label="Search indexed photos" placeholder="Try: Mumbai evening, PhonePe above ₹500" value={search} onChange={e=>setSearch(e.target.value)}/>{search&&<button aria-label="Clear search" onClick={()=>setSearch('')}><X size={18}/></button>}</label>
    <div className="pf-filter-row"><div className="pf-category-scroll"><button className={category==='All'?'active':''} onClick={()=>setCategory('All')}>All</button>{categoryCounts.map(cat=><button key={cat} className={category===cat?'active':''} onClick={()=>setCategory(cat)}>{cat}</button>)}</div><button className="pf-filter-trigger" onClick={()=>setShowFilters(true)} aria-label="Open advanced filters"><SlidersHorizontal size={19}/></button></div>
    <div className="pf-results-actions"><span>{visible.length} photos</span>{selectMode?<div className="pf-row"><button onClick={()=>setSelection(new Set(visible.map(p=>p.id)))}>Select all visible</button><button onClick={leaveSelection}>Done</button></div>:<button onClick={()=>{setSelectMode(true);setSelection(new Set())}}>Select</button>}</div>
    {visible.length?<div className="pf-photo-groups">{groupByMonth(visible).map(([month,group])=><section key={month}><h2>{labelForDate(month)}</h2><div className="pf-photo-grid">{group.map(p=><button key={p.id} className={`pf-photo-tile ${selection.has(p.id)?'selected':''}`} title={p.file.name} aria-label={`${p.category}; ${p.file.name}`} onClick={()=>{if(selectMode)toggle(p.id);else setDetail(p.id)}} onContextMenu={e=>{e.preventDefault();setSelectMode(true);toggle(p.id)}}><img src={p.url} loading="lazy" alt=""/>{selectMode&&<span className="pf-check">{selection.has(p.id)?<Check size={15}/>:null}</span>}</button>)}</div></section>)}</div>:<div className="pf-empty-collection"><Search size={22}/>No results. Try another phrase or clear your filters.</div>}
   </>}
   {page==='collections'&&<><div className="pf-page-title"><div><h1>Collections</h1><p>Automatic categories and personal smart collections</p></div><button onClick={()=>setPage('photos')}>Photos</button></div><div className="pf-collection-grid">{categoryCounts.map(c=>{const cover=photos.find(p=>p.category===c);return <button className="pf-collection" key={c} onClick={()=>openCollection(c)}>{cover&&<img src={cover.url} alt=""/>}<span><strong>{c}</strong><small>{countByCategory.get(c)} photos</small></span><ChevronRight size={16}/></button>})}{Object.entries(collections).map(([name,ids])=><button key={name} className="pf-collection" onClick={()=>{setCategory('All');setSearch('');setPage('photos');setNotice(`Collection “${name}” has ${ids.length} photos. Select items to share; originals stay in place.`)}}><Album size={26}/><span><strong>{name}</strong><small>{ids.length} photos · virtual</small></span><ChevronRight size={16}/></button>)}</div>{!photos.length&&<div className="pf-empty-collection">No collections yet. Choose Gallery or Folder to begin.</div>}</>}
   {loading&&<div className="pf-progress" role="status"><Sparkles size={18}/><div><b>Analyzing photos on your device</b><small>{status}</small><progress max={total} value={processed}/></div><button onClick={()=>{generation.current++;setLoading(false);setStatus('Scan stopped; originals unchanged.')}}>Stop</button></div>}
   {!loading&&status&&photos.length>0&&<p className="pf-session-status">{status}</p>}
   {notice&&<div className="pf-toast" role="status"><span>{notice}</span><button aria-label="Dismiss notice" onClick={()=>setNotice('')}><X size={18}/></button></div>}
  </div>
  <nav className="pf-nav" aria-label="PhotoZest navigation"><button onClick={()=>{setPage('home');leaveSelection()}} className={page==='home'?'active':''}><Sparkles size={20}/>Home</button><button onClick={()=>{setPage('photos');leaveSelection()}} className={page==='photos'?'active':''}><ImageIcon size={20}/>Photos</button><button onClick={()=>{setPage('collections');leaveSelection()}} className={page==='collections'?'active':''}><Album size={20}/>Collections</button></nav>
  {selectMode&&selection.size>0&&page==='photos'&&<div className="pf-selection-bar"><b>{selectedPhotos.length} selected</b><div>{photoZestFeatures.browserSharing&&<button onClick={()=>void share()}><Share2 size={18}/>Share originals</button>}{photoZestFeatures.smartCollections&&<button onClick={()=>setNewCollection(true)}><Folder size={18}/>Create collection</button>}</div></div>}
  {showFilters&&<div className="pf-scrim" onClick={()=>setShowFilters(false)}><section className="pf-modal" onClick={e=>e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Advanced photo filters"><div className="pf-modal-heading"><h2>Filters</h2><button aria-label="Close filters" onClick={()=>setShowFilters(false)}><X/></button></div><label>Location<select value={place} onChange={e=>setPlace(e.target.value)}><option>All places</option>{Array.from(cityCounts.keys()).sort().map(c=><option key={c}>{c}</option>)}</select></label><p>Search can combine locations, dates, categories and recognized payment attributes. Location without image GPS stays unknown.</p><div className="pf-row"><button onClick={()=>{setPlace('All places');setCategory('All');setSearch('')}}>Reset</button><button className="pf-primary" onClick={()=>setShowFilters(false)}>View {visible.length} photos</button></div></section></div>}
  {newCollection&&photoZestFeatures.smartCollections&&<div className="pf-scrim" onClick={()=>setNewCollection(false)}><section className="pf-modal" onClick={e=>e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Create smart collection"><div className="pf-modal-heading"><h2>Create collection</h2><button onClick={()=>setNewCollection(false)} aria-label="Close"><X/></button></div><p>Collections are virtual in your browser. They never duplicate or relocate image files. Moving originals into real folders requires the Android app.</p><input maxLength={60} value={collectionName} onChange={e=>setCollectionName(e.target.value)} placeholder="Collection name" aria-label="Collection name"/><button disabled={!collectionName.trim()||!selectedPhotos.length} className="pf-primary" onClick={createCollection}>Create from {selectedPhotos.length} photos</button></section></div>}
  {focused&&<div className="pf-scrim" onClick={()=>setDetail(null)}><section className="pf-modal pf-detail" onClick={e=>e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Photo details"><div className="pf-modal-heading"><h2>Photo details</h2><button onClick={()=>setDetail(null)} aria-label="Close details"><X/></button></div><img src={focused.url} alt={`Preview ${focused.file.name}`}/><p>{focused.file.name}</p><div className="pf-detail-info"><span>{focused.width} × {focused.height}</span><span>{focused.city||'Location unknown'}</span><span>{focused.captured||'Capture date unknown'}</span><span>{focused.reason}</span>{focused.payment&&<span>Payment evidence: {focused.payment.provider} · {focused.payment.amount?`₹${focused.payment.amount}`:'Amount not identified'} · {focused.payment.status} (verify manually)</span>}</div><label>Category<select value={focused.category} onChange={e=>setPhotos(prev=>prev.map(p=>p.id===focused.id?{...p,category:e.target.value as Category}:p))}>{categories.map(c=><option key={c}>{c}</option>)}</select></label><label>City (manual correction)<input value={focused.city||''} onChange={e=>setPhotos(prev=>prev.map(p=>p.id===focused.id?{...p,city:e.target.value.trim()||null}:p))} placeholder="Unknown"/></label><p className="pf-tags">{focused.tags.map(t=><span key={t}>{t}</span>)}</p></section></div>}
  <p className="pf-footnote">Privacy-first: no photo uploads. On the web, smart collections don't move originals; native folder moves remain exclusive to PhotoZest Android.</p>
 </div>;
}
