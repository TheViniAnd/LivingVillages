package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.Village;
import ru.livingvillages.paper.HallDisplays;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HallAnchorTest {
    @Test void stiltPorchDoesNotMoveNoticesIntoTheFrontWall(){var b=new Village.Building();b.width=13;b.depth=14;b.noticeDepth=11;b.workY=70;var v=new Village();var anchors=HallDisplays.anchors(v,b);assertEquals(10,anchors.size());assertTrue(anchors.stream().allMatch(a->a.point().z()<8));assertEquals(10,anchors.stream().map(a->a.point().x()+":"+a.point().y()+":"+a.point().z()).distinct().count());}
    @Test void oldSavedHallsKeepTheirPreviousSixPositions(){var b=new Village.Building();b.width=11;b.depth=12;assertEquals(6,HallDisplays.point(b,0,1).z());b.noticeDepth=9;assertEquals(4,HallDisplays.point(b,0,1).z());}
    @Test void rotatedNoticePositionsStayInsideTheReservedFootprint(){for(int rotation=0;rotation<4;rotation++){var b=new Village.Building();b.rotation=rotation;b.width=rotation%2==0?13:14;b.depth=rotation%2==0?14:13;b.noticeDepth=11;for(var a:HallDisplays.anchors(new Village(),b)){assertTrue(a.point().x()>=b.x && a.point().x()<b.x+b.width);assertTrue(a.point().z()>=b.z && a.point().z()<b.z+b.depth);}}}
}
