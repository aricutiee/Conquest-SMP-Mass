"""Private Conquest TSV -> Obsidian reports and SVG charts. Standard library only."""
import argparse, csv, math, collections, datetime as dt, html, json
from pathlib import Path
from zoneinfo import ZoneInfo

FIELDS=['time','epoch','type','player','session','staff','detail']
def load(path):
 rows=[];bad=0
 for values in csv.reader(Path(path).open(encoding='utf-8'),delimiter='\t'):
  if len(values)!=7: bad+=1;continue
  try:r=dict(zip(FIELDS,values));r['time']=int(r['time'])/1000;rows.append(r)
  except ValueError:bad+=1
 return sorted(rows,key=lambda r:r['time']),bad

def sessions(rows):
 data={}
 for r in rows:
  if not r['session']:continue
  s=data.setdefault(r['session'],dict(player=r['player'],start=r['time'],end=r['time'],closed=False,staff=False,afk=0,samples=0,reason='unknown'))
  s['end']=max(s['end'],r['time']);s['staff']|=r['staff']=='true'
  if r['type']=='JOIN':s['start']=r['time'];s['reason']=r['detail']
  if r['type']=='QUIT':s['closed']=True
  if r['type']=='CENSOR':s['closed']=False
  if r['type']=='SAMPLE':s['samples']+=1;s['afk']+=r['detail']=='afk'
 return data

def svg(path,title,labels,values,unit='',line=False):
 w,h=1000,440;left=65;bottom=310;top=65;maxv=max([v or 0 for v in values]+[1]);parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="#171020"/><g font-family="sans-serif" fill="#eadcff"><text x="30" y="32" font-size="20">{html.escape(title)}</text>']
 if not labels:parts.append('<text x="65" y="160">Not enough observations yet.</text>')
 else:
  step=900/max(1,len(labels));points=[]
  for i,(label,value) in enumerate(zip(labels,values)):
   x=left+(i+.5)*step;v=value or 0;y=bottom-v/maxv*(bottom-top)
   if value is not None:
    if line:points.append(f'{x},{y}');parts.append(f'<circle cx="{x}" cy="{y}" r="3" fill="#d4acff"/>')
    else:parts.append(f'<rect x="{x-step*.35}" y="{y}" width="{step*.7}" height="{bottom-y}" fill="#9757db"/>')
   if len(labels)<20 or i%max(1,len(labels)//12)==0:
    parts.append(f'<text x="{x}" y="{bottom+20}" font-size="11" text-anchor="end" transform="rotate(-35 {x} {bottom+20})">{html.escape(str(label))}</text>')
   if not line:parts.append(f'<text x="{x}" y="{max(top,y-8)}" font-size="12" text-anchor="middle">{("pending" if value is None else f"{value:.1f}{unit}")}</text>')
  if line and points:parts.append(f'<polyline points="{" ".join(points)}" fill="none" stroke="#b77af5" stroke-width="2"/>')
  parts.append(f'<text x="8" y="70" font-size="12">{maxv:.1f}{unit}</text><text x="35" y="310" font-size="12">0</text>')
 parts.append('</g></svg>');path.write_text(''.join(parts),encoding='utf-8')

def report(rows,start,end,asof,zone,dest,title,bad):
 dest.mkdir(parents=True,exist_ok=True);assets=dest/'Charts';assets.mkdir(exist_ok=True)
 all_s=sessions(rows);excluded={s['player'] for s in all_s.values() if s['staff']}
 clean=[r for r in rows if r['player'] not in excluded];window=[r for r in clean if start<=r['time']<end]
 ss={k:s for k,s in all_s.items() if s['player'] not in excluded};ws={k:s for k,s in ss.items() if s['start']<end and s['end']>=start}
 players={r['player'] for r in window if r['player']}
 first={}
 for s in ss.values():first[s['player']]=min(first.get(s['player'],math.inf),s['start'])
 fmt=lambda t:dt.datetime.fromtimestamp(t,zone).strftime('%Y-%m-%d %H:%M')
 text=[f'# {title}',f'Generated: {fmt(asof)} ({zone.key})',f'Period: {fmt(start)} to {fmt(end)}',f'Latest collected observation: {fmt(rows[-1]["time"]) if rows else "No observations"}',
       f'Coverage starts: {fmt(rows[0]["time"]) if rows else "Waiting for launch"}. Earlier player history is unknown.',
       f'**Unique observed players: {len(players)} | Sessions overlapping period: {len(ws)} | Excluded staff identities: {len(excluded)}**',
       'Staff/OP and recognized NPCs are excluded. First observed means first seen since tracking began, not necessarily first ever join. Time outside the AFK zone does not prove active play. Correlations do not establish why a player left.',
       f'Malformed rows skipped: {bad}. Missing or interrupted sessions are censored, not counted as voluntary exits.']
 if not rows or asof-rows[-1]['time']>600:text.append('> Data is empty or more than ten minutes behind report generation. Do not interpret missing observations as zero players.')
 def chart(slug,heading,labels,values,note='',unit='',line=False):
  svg(assets/(slug+'.svg'),heading,labels,values,unit,line)
  text.extend([f'## {heading}',f'![](Charts/{slug}.svg)',note])
 daily=collections.defaultdict(set);new=collections.Counter()
 for r in window:
  if r['player']:daily[dt.datetime.fromtimestamp(r['time'],zone).strftime('%m-%d')].add(r['player'])
 for p,t in first.items():
  if start<=t<end:new[dt.datetime.fromtimestamp(t,zone).strftime('%m-%d')]+=1
 labels=sorted(daily)
 chart('daily-players','Daily unique players',labels,[len(daily[d]) for d in labels])
 chart('new-players','First-observed players',labels,[new[d] for d in labels])
 chart('returning-players','Returning players by day',labels,[sum(dt.datetime.fromtimestamp(first[p],zone).strftime('%m-%d')!=d for p in daily[d]) for d in labels],'Returned on a later local calendar day, within observed history.')
 buckets=collections.defaultdict(set);afk=collections.defaultdict(set)
 for r in window:
  if r['type']=='HEARTBEAT':buckets.setdefault(int(r['time']//60),set())
  if r['type']=='SAMPLE':
   key=int(r['time']//60);buckets[key].add(r['player'])
   if r['detail']=='afk':afk[key].add(r['player'])
 keys=sorted(buckets)
 # Hourly peak from minute samples, keeps long reports readable.
 peaks=collections.defaultdict(int);afkpeaks=collections.defaultdict(int)
 for k in keys:peaks[k//60]=max(peaks[k//60],len(buckets[k]));afkpeaks[k//60]=max(afkpeaks[k//60],len(afk[k]))
 hours=sorted(peaks);hl=[dt.datetime.fromtimestamp(k*3600,zone).strftime('%m-%d %Hh') for k in hours]
 chart('concurrency','Hourly peak observed online',hl,[peaks[k] for k in hours],'One-minute samples. Missing hours are unknown, not zero.',line=True)
 chart('afk','Hourly peak in AFK zone',hl,[afkpeaks[k] for k in hours],line=True)
 bins=[0]*7;thresholds=[300,900,1800,3600,7200,10800,14400]
 completed=[s for s in ws.values() if s['closed'] and start<=s['start']<end and s['reason']=='login']
 for s in completed:
  duration=s['end']-s['start'];index=next((i for i,t in enumerate(thresholds[:6]) if duration<t),6);bins[index]+=1
 # Keep 3-4h and 4h+ separate.
 bins=[sum(lo<=s['end']-s['start']<hi for s in completed) for lo,hi in [(0,300),(300,900),(900,3600),(3600,7200),(7200,10800),(10800,14400),(14400,math.inf)]]
 chart('sessions','Completed session lengths',['<5m','5-15m','15-60m','1-2h','2-3h','3-4h','4h+'],bins,'Login-to-disconnect sessions starting in this period. Startup, launch, crash and still-open sessions excluded. AFK included.')
 chart('long-play','Players with long observed sessions',['1h+','2h+','3h+','4h+'],[len({s['player'] for s in ws.values() if min(s['end'],end)-max(s['start'],start)>=h*3600}) for h in [1,2,3,4]],'Distinct players with an observed session overlap at least this long. A player may appear in multiple bars; includes AFK.')
 cohort={p:t for p,t in first.items() if start<=t<end};rates=[];notes=[]
 coverage_end=max((r['time'] for r in rows),default=0)
 for day in [1,3,7]:
  eligible={p:t for p,t in cohort.items() if t+(day+1)*86400<=min(asof,coverage_end)}
  success=sum(any(s['player']==p and s['start']<t+(day+1)*86400 and s['end']>=t+day*86400 for s in ss.values()) for p,t in eligible.items())
  rates.append(success/len(eligible)*100 if eligible else None);notes.append(f'D{day}: {success}/{len(eligible)} mature players')
 chart('retention','Observed D1 / D3 / D7 retention',['D1','D3','D7'],rates,'; '.join(notes)+'. Presence in the 24-hour window beginning 1, 3 or 7 days after first observation. Incomplete cohorts pending; coverage gaps can undercount.',unit='%')
 kinds=['PVP_DEATH','KILL','KEY_ALL','REWARD','EVENT_PRESENT','MARK_PRESENT'];rates=[];stay=[];den=[]
 for kind in kinds:
  signals={}
  for r in window:
   if r['type']==kind:signals.setdefault((r['player'],r['session']),r)
  eligible=[]
  for r in signals.values():
   s=ss.get(r['session']);
   if s and (s['end']>=r['time']+900 or (s['closed'] and s['end']>=r['time'])):eligible.append((r,s))
  n=len(eligible);den.append(n)
  rates.append(sum(s['closed'] and 0<=s['end']-r['time']<=300 for r,s in eligible)/n*100 if n else None)
  stay.append(sum(s['end']>=r['time']+900 for r,s in eligible)/n*100 if n else None)
 chart('exit-after','Disconnected within 5 minutes after',['PvP death','Kill','Key all','Reward','Event online','Marker online'],rates,'First occurrence per player/session/type; eligible samples '+str(den)+'. Later reconnects do not erase a disconnect. Signals overlap; these are not causal effects.',unit='%')
 chart('stay-after','Still connected 15 minutes after',['PvP death','Kill','Key all','Reward','Event online','Marker online'],stay,'Same eligible samples as the disconnect chart. Includes AFK; no prediction for censored follow-up.',unit='%')
 exposure=collections.defaultdict(set)
 for r in window:
  if r['type'] in ['EVENT_PRESENT','MARK_PRESENT']:exposure[r['detail']].add(r['player'])
 chart('events','Players online during events and markers',list(exposure),[len(v) for v in exposure.values()],'Online presence is not verified participation. One-minute sampling may miss brief visits.')
 participants=collections.defaultdict(set)
 for r in window:
  if r['type']=='PARTICIPATE':participants[r['detail']].add(r['player'])
 chart('participation','Verified event interactions',list(participants),[len(v) for v in participants.values()],'Currently instrumented: damage to the active Juggernaut/Warlord. Other event interactions are not inferred.')
 chart('pvp','Unique players involved in combat',['Scored a kill','Died in PvP'],[len({r['player'] for r in window if r['type']==kind}) for kind in ['KILL','PVP_DEATH']],'PvP death uses Bukkit killer attribution. Environmental or uncredited combat deaths are separate.')
 text+=['## Interpretation','Compare like-for-like periods and sample sizes. Event attendees self-select; rewards, kills and deaths can occur together. A disconnect is not necessarily rage-quitting. Treat cohorts spanning downtime or missing data cautiously.','## Recommended next measurements','First-session onboarding completion; promotion source and invite attribution; client disconnect reason versus server kick; TPS/MSPT alongside exits; return after base loss; progression milestones; race choice and rerolls; shop purchases; structured event enrollment and reward delivery for every event.']
 (dest/'Report.md').write_text('\n\n'.join(text)+'\n',encoding='utf-8')
 return dict(players=len(players),sessions=len(ws),completed=len(completed))

def main():
 p=argparse.ArgumentParser();p.add_argument('--input',required=True);p.add_argument('--vault',required=True);p.add_argument('--timezone',default='Africa/Casablanca');p.add_argument('--now',type=float);a=p.parse_args()
 zone=ZoneInfo(a.timezone);now=a.now or dt.datetime.now().timestamp();local=dt.datetime.fromtimestamp(now,zone);today=local.replace(hour=0,minute=0,second=0,microsecond=0);monday=today-dt.timedelta(days=today.weekday())
 rows,bad=load(a.input)
 if rows:
  latest=rows[-1]['epoch'];rows=[r for r in rows if r['epoch']==latest]
 root=Path(a.vault)/'Conquest SMP Analytics';overall=Path(a.vault)/'Conquest SMP Overall Analytics'
 periods=[('Today',today.timestamp(),now,root/'Today'),('Last 7 days',(local-dt.timedelta(days=7)).timestamp(),now,overall/'Last 7 Days'),('This week',monday.timestamp(),now,overall/'This Week'),('Last week',(monday-dt.timedelta(days=7)).timestamp(),monday.timestamp(),overall/'Last Week'),('Since SMP launch',rows[0]['time'] if rows else now,now,overall/'Since Launch')]
 for title,start,end,path in periods:report(rows,start,end,now,zone,path,title,bad)
 stamp=local.strftime('%Y-%m-%d/%H-%M');snapshot=root/'History'/stamp
 report(rows,today.timestamp(),now,now,zone,snapshot,'Today snapshot '+local.strftime('%Y-%m-%d %H:%M'),bad)
 root.mkdir(parents=True,exist_ok=True)
 (root/'Index.md').write_text('# Conquest SMP Analytics\n\nUpdated '+local.isoformat()+'\n\n[[Conquest SMP Analytics/Today/Report|Today]]\n\n[[Conquest SMP Overall Analytics/Last 7 Days/Report|Last 7 days]]\n\n[[Conquest SMP Overall Analytics/This Week/Report|This week]]\n\n[[Conquest SMP Overall Analytics/Last Week/Report|Last week]]\n\n[[Conquest SMP Overall Analytics/Since Launch/Report|Since launch]]\n\nFour-hour snapshots are under History. No data is reconstructed before collection began.\n',encoding='utf-8')
 print(json.dumps({'reports':6,'latest_observation':rows[-1]['time'] if rows else None,'malformed_rows':bad,'index':str(root/'Index.md')}))
if __name__=='__main__':main()
