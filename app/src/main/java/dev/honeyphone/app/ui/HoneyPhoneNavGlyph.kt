package dev.honeyphone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import dev.honeyphone.app.model.AppDestination
import dev.honeyphone.app.model.HoneypotProfile

@Composable
fun HoneyPhoneNavGlyph(destination: AppDestination, selected: Boolean, modifier: Modifier = Modifier) {
    val ink=if(selected) HoneyStyle.Ink else HoneyStyle.Muted
    val accent=HoneyStyle.Amber
    Canvas(modifier.size(26.dp)) {
        val u=size.minDimension/28f
        val sw=2.1f*u
        fun p(x:Float,y:Float)=Offset(x*u,y*u)
        fun line(x1:Float,y1:Float,x2:Float,y2:Float,color:androidx.compose.ui.graphics.Color=ink,width:Float=sw) {
            drawLine(color,p(x1,y1),p(x2,y2),width,cap=StrokeCap.Round)
        }
        fun circle(x:Float,y:Float,r:Float,color:androidx.compose.ui.graphics.Color=ink,width:Float=sw,fill:Boolean=false) {
            drawCircle(color,r*u,p(x,y),style=if(fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width))
        }
        fun hexagon(cx:Float,cy:Float,r:Float,color:androidx.compose.ui.graphics.Color=ink,width:Float=sw) {
            val path=Path().apply {
                for(i in 0..5) {
                    val angle=Math.PI/3.0*i-Math.PI/6.0
                    val point=p(cx+r*kotlin.math.cos(angle).toFloat(),cy+r*kotlin.math.sin(angle).toFloat())
                    if(i==0) moveTo(point.x,point.y) else lineTo(point.x,point.y)
                }
                close()
            }
            drawPath(path,color,style=Stroke(width,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        when(destination) {
            AppDestination.HOME -> {
                val center=p(14f,18f)
                listOf(7f,12f,17f).forEach { radius ->
                    drawArc(ink,225f,90f,false,Offset(center.x-radius*u,center.y-radius*u),Size(radius*2*u,radius*2*u),style=Stroke(sw,cap=StrokeCap.Round))
                }
                circle(14f,19f,1.8f,accent,fill=true)
            }
            AppDestination.HONEYPOT -> {
                hexagon(14f,12f,9.2f)
                line(9.4f,19.2f,14f,24f)
                line(18.6f,19.2f,14f,24f)
                circle(14f,12f,2.2f,accent,fill=true)
            }
            AppDestination.HISTORY -> {
                line(5f,22f,5f,7f)
                line(5f,22f,24f,22f)
                val path=Path().apply { moveTo(7f*u,17f*u);lineTo(11f*u,13f*u);lineTo(15f*u,16f*u);lineTo(21f*u,8f*u) }
                drawPath(path,ink,style=Stroke(sw,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                circle(21f,8f,1.8f,accent,fill=true)
            }
            AppDestination.SETTINGS -> {
                circle(14f,14f,7.5f)
                circle(14f,14f,2.8f,accent)
                line(14f,2.8f,14f,5.2f)
                line(14f,22.8f,14f,25.2f)
                line(2.8f,14f,5.2f,14f)
                line(22.8f,14f,25.2f,14f)
            }
            AppDestination.PROFILE -> {
                circle(14f,9.3f,4.1f,accent)
                val shoulders=Path().apply {
                    moveTo(5f*u,24f*u)
                    cubicTo(5.8f*u,18.7f*u,9.1f*u,16.2f*u,14f*u,16.2f*u)
                    cubicTo(18.9f*u,16.2f*u,22.2f*u,18.7f*u,23f*u,24f*u)
                }
                drawPath(shoulders,ink,style=Stroke(sw,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                line(5f,24f,9f,24f,ink)
                line(19f,24f,23f,24f,ink)
            }
            else -> circle(14f,14f,8f)
        }
    }
}

@Composable
fun HoneypotScenarioGlyph(profile:HoneypotProfile,selected:Boolean,modifier:Modifier=Modifier) {
    val ink=if(selected)HoneyStyle.Ink else HoneyStyle.Muted
    Canvas(modifier.size(26.dp)) {
        val u=size.minDimension/28f
        val sw=1.9f*u
        fun p(x:Float,y:Float)=Offset(x*u,y*u)
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(ink,p(x1,y1),p(x2,y2),sw,cap=StrokeCap.Round)
        fun window() = drawRoundRect(ink,p(3f,4f),Size(22f*u,20f*u),CornerRadius(3f*u),style=Stroke(sw))
        when(profile) {
            HoneypotProfile.LOGIN_PORTAL -> {
                window()
                line(4f,9f,24f,9f)
                drawCircle(ink,2.2f*u,p(10f,14f),style=Stroke(sw))
                drawArc(ink,205f,130f,false,p(6.5f,15.5f),Size(7f*u,6f*u),style=Stroke(sw,cap=StrokeCap.Round))
                line(16f,14f,22f,14f)
                line(16f,18f,22f,18f)
            }
            HoneypotProfile.ADMIN_CONSOLE -> {
                window()
                line(4f,9f,24f,9f)
                line(10f,10f,10f,23f)
                line(13f,14f,17f,14f)
                line(13f,18f,22f,18f)
                line(13f,22f,19f,22f)
            }
            HoneypotProfile.COMPROMISED_SITE -> {
                window()
                line(4f,9f,24f,9f)
                val crack=Path().apply {
                    moveTo(17f*u,10.5f*u)
                    lineTo(14f*u,15f*u)
                    lineTo(17f*u,17f*u)
                    lineTo(13f*u,22f*u)
                }
                drawPath(crack,ink,style=Stroke(sw,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                line(7f,14f,11f,14f)
                line(7f,18f,10f,18f)
            }
        }
    }
}
