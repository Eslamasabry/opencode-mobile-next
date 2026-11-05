package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Builds one Phosphor glyph: the 256 × 256 artwork scaled to a 24 dp box, filled in one colour (the caller tints it).
 * Each part is `path data to alpha`; duotone glyphs carry a 0.2 background layer. [mirror] glyphs point along the
 * reading direction and flip in RTL through the vector's own auto-mirror (B-KIT-001), so callers never flip them.
 */
internal fun phosphor(name: String, mirror: Boolean, vararg parts: Pair<String, Float>): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
            autoMirror = mirror,
        )
        .apply { parts.forEach { (d, alpha) -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black), fillAlpha = alpha) } }
        .build()

/** The three bundled Phosphor fonts (Dart families `AppPhosphorRegular`, `AppPhosphorDuotone`, `AppPhosphorFill`). */
internal enum class PhosphorFamily { Regular, Duotone, Fill }

/** One glyph of a Phosphor font: its code point and family (Dart `IconData(codePoint, fontFamily:)`). */
internal class PhosphorGlyph(val codePoint: Int, val family: PhosphorFamily)

/** The duotone navigation glyphs' quiet background layer by the selected glyph's code point (Dart `_duotoneBackgrounds`). */
internal val duotoneBackgrounds: Map<Int, Int> = mapOf(0xe17f to 0xe17e, 0xe25b to 0xe25a, 0xe0d1 to 0xe0d0) // NV: code points of lib/ui/kit/kit_icon.dart _duotoneBackgrounds

/** Every [KitIcons] glyph by its vector name: `name=<hex code point><R|D|F family>`, generated from lib/ui/app_iconography.dart at 8302de537. */
private const val GLYPH_DATA = "workspace=e17eR;workspaceSelected=e17fD;files=e25aR;filesSelected=e25bD;activity=e0d0R;activitySelected=e0d1D;more=e1feR;menu=e208R;add=e3d4R;send=e08eR;stop=e46cF;settings=e434R;settingsGear=e270R;search=e30cR;branch=e278R;terminal=eae8R;copy=e1ccR;expand=e0a6R;close=e4f6R;back=e058R;retry=e036R;externalLink=e5deR;review=e27cR;chevronRight=e13aR;chevronDown=e136R;chevronUp=e13cR;check=e182R;info=e2ceR;warning=e4e0R;error=e4f8R;cloud=e1aaR;cloudOff=e1b6R;server=e2a0R;model=e610R;storage=e29eR;heartbeat=e2acR;agent=e762R;tools=e5d4R;extensions=e596R;keyboard=e2d8R;guide=e0e6R;bug=e5f4R;github=e576F;appearance=e6c8R;privacy=e40cR;diagnostics=e000R;usage=e150R;link=e2e2R;unlink=e2e4R;attach=e39aR;download=e20cR;upload=e4c0R;file=e230R;code=e1bcR;archive=e00cR;delete=e4a6R;edit=e3b4R;crop=e1d4R;pin=e3e2R;unpin=e3e4R;computer=e560R;folderAdd=e25eR;question=e3e8R;permissions=e2d6R;clock=e19aR;star=e46aR;starFilled=e46aF;mic=e326R;image=e2caR;camera=e10eR;accessibility=ecfeR;account=e4c4R;addCircle=e3d6R;alignLeft=e484R;wrapText=e482R;article=e0a8R;batteryCharging=e0baR;batteryWarning=e0c8R;blocked=e3deR;bookmark=e0eaR;bookmarks=e5f0R;browser=e0f4R;calendar=e10aR;cameraOff=e110R;category=ec5eR;chat=e168R;checkCircle=e184R;checkboxChecked=e186R;checkboxEmpty=e45eR;checklist=eadcR;checks=e53aR;chevronLeft=e138R;clearAll=ec54R;cloudCheck=e1b0R;collapse=e09eR;contrast=e18cR;textSize=e6eeR;cut=eae0R;darkMode=e330R;leaf=e2daR;dataObject=e860R;database=e1deR;deviceOff=ee46R;down=e03eR;editNote=e34cR;editOff=ecf6R;experiments=e79eR;feedback=e17aR;fileText=e23aR;fileUpload=e61eR;filter=e268R;filterOff=e26cR;folderOpen=e256R;folders=e260R;fork=e27eR;forward=e06cR;function=ebe4R;globe=e288R;hidden=e224R;history=e1a0R;idea=e2dcR;imageBroken=e7a8R;images=e836R;inbox=e010R;kanban=eb54R;launch=e3feR;layers=e466R;lightMode=e472R;lightning=e2deR;locked=e308R;login=e428R;lowPriority=e126R;manageAccount=e4ccR;nested=e046R;network=eddeR;networkCheck=e4eaR;networkOff=eddcR;note=e348R;notificationImportant=e5eaR;outbox=ee52R;package=e390R;paste=e198R;person=e4c2R;personRemove=e4ceR;phone=e1e0R;pause=e39eR;play=e3d0R;playCircle=e3d2R;policy=e40cR;privacyWarning=e412R;processor=e610R;projects=e102R;qrCode=e3e6R;queueAdd=e2f8R;radioEmpty=e18aR;radioSelected=eb08R;removeCircle=e32cR;reply=e024R;restart=e038R;restore=e1a0R;returnKey=e782R;save=e248R;searchList=ebe0R;secureNetwork=e40cR;settingsAdvanced=e272R;shield=e40aR;shieldFilled=e40aF;sparkle=e6a2R;speakUser=eca8R;speed=ee74R;statusDot=e18aF;stopCircle=e46eR;support=e584R;supportQuestion=e3e8R;swap=e0a0R;sync=e094R;systemDownload=e20cR;systemTheme=e18cR;text=e484R;textShort=e484R;textSnippet=e484R;timeline=e5a2R;timer=e492R;touch=ec90R;unarchive=ee52R;undo=e08aR;unfoldLess=e532R;unfoldMore=e140R;usageRing=eaa6R;visible=e220R;volume=e44aR;waiting=e2b6R;waitingEmpty=e2b2R;waitingStart=e2b4R;waveform=e802R;zip=e958R" // NV: generated table of code points and families, one line per Dart IconData in app_iconography.dart

internal val glyphTable: Map<String, PhosphorGlyph> by lazy {
    GLYPH_DATA.split(';').associate { row ->
        val (name, spec) = row.split('=')
        val family = when (spec.last()) { 'R' -> PhosphorFamily.Regular; 'D' -> PhosphorFamily.Duotone; else -> PhosphorFamily.Fill }
        name to PhosphorGlyph(spec.dropLast(1).toInt(16), family) // NV: hex parse
    }
}
