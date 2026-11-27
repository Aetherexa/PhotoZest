import {describe,expect,it} from 'vitest';
import {classifyPhoto,cityFromGps,timeOfDay,parsePayment,likelyDuplicate,screenshotName} from './engine';
import type {Quality,VisionEvidence} from './engine';
const quality=(extra:Partial<Quality>={}):Quality=>({width:3000,height:2000,brightness:0.55,sharpness:0.78,contrast:0.26,highlights:0.01,shadows:0.01,hash:123n,...extra});
const evidence=(labels:{name:string;score:number}[]=[],persons:VisionEvidence['persons']=[]):VisionEvidence=>({labels,persons});
describe('PhotoZest cross-platform vision policy',()=>{
 it('does not call a wide unrecognized photograph a landscape',()=>{expect(classifyPhoto(quality(),null).category).toBe('Needs review')});
 it('recognizes supported scenery',()=>{expect(classifyPhoto(quality(),evidence([{name:'mountain',score:.95}])).category).toBe('Landscapes')});
 it('prioritizes group photo over restaurant background',()=>{expect(classifyPhoto(quality(),evidence([{name:'food',score:.97}],[{bbox:[100,100,400,450],score:.9},{bbox:[700,100,390,440],score:.9}])).category).toBe('Group photos')});
 it('keeps conflicting evidence for review',()=>{expect(classifyPhoto(quality(),evidence([{name:'food',score:.91},{name:'mountain',score:.89}])).category).toBe('Needs review')});
 it('does not create social-ready tag on clipped image',()=>{expect(classifyPhoto(quality({highlights:.5}),evidence([])).tags).not.toContain('Social-ready')});
 it('does not confuse research with sea',()=>{expect(classifyPhoto(quality(),evidence([{name:'research',score:.99}])).category).toBe('Needs review')});
 it('does not confuse two different ratios as near duplicates',()=>{expect(likelyDuplicate(quality(),quality({width:2000,height:3000}))).toBe(false)});
});
describe('metadata and conservative receipts',()=>{
 it('resolves Mumbai and Pune GPS approximately',()=>{expect(cityFromGps(19.076,72.878)).toBe('Mumbai');expect(cityFromGps(18.52,73.86)).toBe('Pune')});
 it('refuses places without GPS',()=>expect(cityFromGps(null,null)).toBeNull());
 it('maps capture hour to period of day',()=>expect(timeOfDay('2026-11-25 18:22')).toBe('Evening'));
 it('sees screenshot name',()=>expect(screenshotName('.trashed-Screenshot_2026-11-25.png')).toBe(true));
 it('does not treat incoming funds as spending',()=>{const x=parsePayment('Google Pay money received from Neha ₹1,200 successfully');expect(x?.direction).toBe('received');expect(x?.amount).toBe(1200)});
 it('does not treat a declined payment as successful',()=>expect(parsePayment('PhonePe paid to Cafe ₹500 payment failed')?.status).toBe('failed'));
});
