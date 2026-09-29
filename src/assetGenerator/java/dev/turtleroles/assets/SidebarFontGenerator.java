package dev.turtleroles.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Five-pixel small capitals in the scoreboard's light purple. */
final class SidebarFontGenerator {
    private static final String CHARS="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-.";
    private static final String[] GLYPHS={
        "01110/10001/11111/10001/10001", "11110/10001/11110/10001/11110",
        "01111/10000/10000/10000/01111", "11110/10001/10001/10001/11110",
        "11111/10000/11110/10000/11111", "11111/10000/11110/10000/10000",
        "01111/10000/10111/10001/01111", "10001/10001/11111/10001/10001",
        "111/010/010/010/111", "00111/00010/00010/10010/01100",
        "10001/10010/11100/10010/10001", "10000/10000/10000/10000/11111",
        "10001/11011/10101/10001/10001", "10001/11001/10101/10011/10001",
        "01110/10001/10001/10001/01110", "11110/10001/11110/10000/10000",
        "01110/10001/10101/10010/01101", "11110/10001/11110/10010/10001",
        "01111/10000/01110/00001/11110", "11111/00100/00100/00100/00100",
        "10001/10001/10001/10001/01110", "10001/10001/10001/01010/00100",
        "10001/10001/10101/11011/10001", "10001/01010/00100/01010/10001",
        "10001/01010/00100/00100/00100", "11111/00010/00100/01000/11111",
        "01110/10011/10101/11001/01110", "010/110/010/010/111",
        "11110/00001/01110/10000/11111", "11110/00001/01110/00001/11110",
        "10010/10010/11111/00010/00010", "11111/10000/11110/00001/11110",
        "01111/10000/11110/10001/01110", "11111/00001/00010/00100/00100",
        "01110/10001/01110/10001/01110", "01110/10001/01111/00001/11110",
        "0/1/0/1/0", "000/000/111/000/000", "0/0/0/0/1"
    };
    static int ink() {
        return 0xFFB477FF;
    }
    static void write(Path textures,Path fonts,Path previews) throws IOException {
        BufferedImage atlas=new BufferedImage(16*6,3*8,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<CHARS.length();i++) {
            String[] rows=GLYPHS[i].split("/");
            for(int y=0;y<5;y++)for(int x=0;x<rows[y].length();x++)
                if(rows[y].charAt(x)=='1')atlas.setRGB((i%16)*6+x,(i/16)*8+y+2,ink());
        }
        ImageIO.write(atlas,"png",textures.resolve("sidebar.png").toFile());
        StringBuilder chars=new StringBuilder();
        for(int row=0;row<3;row++) {
            if(row>0)chars.append(',');
            chars.append('"');
            for(int col=0;col<16;col++) {
                int i=row*16+col;
                chars.append(i<CHARS.length()?String.valueOf(CHARS.charAt(i)):"\\u0000");
            }
            chars.append('"');
        }
        Files.writeString(fonts.resolve("sidebar.json"),"{\"providers\":[{\"type\":\"space\",\"advances\":{\" \":3}},{\"type\":\"bitmap\",\"file\":\"turtleroles:font/sidebar.png\",\"height\":8,\"ascent\":7,\"chars\":["+chars+"]}]}\n");
        BufferedImage ip=new BufferedImage(atlas.getWidth(),atlas.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<atlas.getHeight();y++)for(int x=0;x<atlas.getWidth();x++)if((atlas.getRGB(x,y)>>>24)!=0)ip.setRGB(x,y,0xFFFFFFFF);
        ImageIO.write(ip,"png",textures.resolve("sidebar_ip.png").toFile());
        Files.writeString(fonts.resolve("sidebar_ip.json"),Files.readString(fonts.resolve("sidebar.json")).replace("sidebar.png","sidebar_ip.png"));
        BufferedImage title=new BufferedImage(atlas.getWidth(),atlas.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<atlas.getHeight();y++)for(int x=0;x<atlas.getWidth();x++)if((atlas.getRGB(x,y)>>>24)!=0){
            double f=Math.clamp(((y%8)-2)/4.0,0,1);int top=0xB78AFF,bottom=0x29143D;
            int red=(int)Math.round(((top>>16)&255)*(1-f)+((bottom>>16)&255)*f),green=(int)Math.round(((top>>8)&255)*(1-f)+((bottom>>8)&255)*f),blue=(int)Math.round((top&255)*(1-f)+(bottom&255)*f);
            title.setRGB(x,y,0xFF000000|(red<<16)|(green<<8)|blue);
        }
        ImageIO.write(title,"png",textures.resolve("sidebar_title.png").toFile());
        Files.writeString(fonts.resolve("sidebar_title.json"),Files.readString(fonts.resolve("sidebar.json")).replace("sidebar.png","sidebar_title.png"));
        String[] demo={"COMBAT","KILLS: 12","DEATHS: 3","STREAK: 4","OTHER","PING: 42MS","PLAYTIME: 2D 7H"};
        BufferedImage preview=new BufferedImage(110*6,demo.length*10*6,BufferedImage.TYPE_INT_ARGB);
        var g=preview.createGraphics();g.setColor(new java.awt.Color(0x202028));g.fillRect(0,0,preview.getWidth(),preview.getHeight());
        for(int line=0;line<demo.length;line++) {
            int cursor=2;
            for(char c:demo[line].toCharArray()) {
                if(c==' '){cursor+=3;continue;}
                String[] rows=GLYPHS[CHARS.indexOf(c)].split("/");
                for(int y=0;y<5;y++)for(int x=0;x<rows[y].length();x++)if(rows[y].charAt(x)=='1'){
                    g.setColor(new java.awt.Color(ink(),true));g.fillRect((cursor+x)*6,(line*10+y+2)*6,6,6);
                }
                cursor+=rows[0].length()+1;
            }
        }
        g.dispose();ImageIO.write(preview,"png",previews.resolve("sidebar-solid.png").toFile());
    }
}
