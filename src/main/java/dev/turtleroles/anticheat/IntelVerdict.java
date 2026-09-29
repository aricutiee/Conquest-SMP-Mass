package dev.turtleroles.anticheat;

import java.time.*;
import java.util.*;

/** Plain-language results, without turning absence of evidence into proof. */
final class IntelVerdict {
    static String field(String detail,String prefix) {
        int start=detail.indexOf(prefix);if(start<0)return "";
        start+=prefix.length();int end=detail.indexOf(';',start);
        return detail.substring(start,end<0?detail.length():end).trim();
    }
    static List<String> lines(List<IntelStore.Entry> entries,ModDetectorReport.Result mods,
            long now,long sessionStart,boolean altReady,boolean grimReady,boolean clientReady) {
        Map<String,IntelStore.Entry> latest=new HashMap<>();
        for(var e:entries)latest.merge(e.source(),e,(a,b)->a.time()>b.time()?a:b);
        var alt=latest.get("ALT");var grim=latest.get("GRIM");var client=latest.get("CLIENT");
        boolean flagged=grim!=null && now-grim.time()<=Duration.ofHours(24).toMillis();
        boolean freshClient=client!=null && client.time()>=sessionStart;
        boolean freshMods=mods.time()>0 && mods.time()>=sessionStart;
        var suspicious=new TreeSet<String>();
        if(freshClient) {
            String match=field(client.detail(),"policy matches=");
            if(!match.isEmpty()&&!match.equals("none"))suspicious.addAll(Arrays.asList(match.split(", ")));
        }
        if(freshMods)suspicious.addAll(mods.suspicious());
        boolean review=alt!=null || flagged || !suspicious.isEmpty();
        boolean incomplete=!altReady||!grimReady||(!freshClient&&!freshMods);
        List<String> lines=new ArrayList<>();
        lines.add("Verdict: "+(review?"REVIEW NEEDED":incomplete?"CHECK INCOMPLETE":"NOTHING SUSPICIOUS DETECTED"));
        lines.add("Alt account: "+(alt!=null?"Possible match":altReady?"No match detected":"Check unavailable"));
        lines.add("Cheating: "+(flagged?"Suspicious activity flagged ("+field(grim.detail(),"Check=")+")":grimReady?"No recent flags detected":"Check unavailable"));
        lines.add("Suspicious mods: "+(!suspicious.isEmpty()?names(suspicious):freshClient||freshMods?"None detected":"Not enough scan data"));
        if(!freshMods && !mods.suspicious().isEmpty())lines.add("Earlier mod signals ("+Instant.ofEpochMilli(mods.time()).atZone(ZoneOffset.UTC).toLocalDate()+"): "+names(mods.suspicious()));
        if(!mods.available() || mods.partial())lines.add("ModDetector: "+(!mods.available()?"Log unavailable":"Partial log coverage"));
        lines.add("Detection is not proof. [Details]");
        return List.copyOf(lines);
    }
    private static String names(Collection<String> names) {
        String value=names.stream().limit(8).collect(java.util.stream.Collectors.joining(", "));
        return value+(names.size()>8?" +"+(names.size()-8)+" more":"");
    }
}
