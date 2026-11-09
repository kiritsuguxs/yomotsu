package eu.kanade.presentation.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object MergeIcons

private const val ICON_DIMENSION = 24f

private inline fun materialIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> ImageVector.Builder,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = ICON_DIMENSION.dp,
    defaultHeight = ICON_DIMENSION.dp,
    viewportWidth = ICON_DIMENSION,
    viewportHeight = ICON_DIMENSION,
    autoMirror = autoMirror,
).block().build()

private inline fun ImageVector.Builder.materialPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = PathFillType.NonZero,
    pathBuilder: PathBuilder.() -> Unit,
) = path(
    fill = SolidColor(Color.Black),
    fillAlpha = fillAlpha,
    stroke = null,
    strokeAlpha = strokeAlpha,
    strokeLineWidth = 1f,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Bevel,
    strokeLineMiter = 1f,
    pathFillType = pathFillType,
    pathBuilder = pathBuilder,
)

val MergeIcons.Merge: ImageVector
    get() {
        if (mergeCache != null) {
            return mergeCache!!
        }
        mergeCache = materialIcon(name = "MergeIcons.Merge") {
            materialPath {
                moveTo(8.71f, 7.71f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(2.59f, -2.59f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                lineToRelative(2.59f, 2.59f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                lineTo(13.0f, 6.83f)
                verticalLineToRelative(5.1f)
                curveToRelative(0.0f, 1.06f, 0.42f, 2.08f, 1.17f, 2.83f)
                lineToRelative(4.12f, 4.12f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                reflectiveCurveToRelative(-1.02f, 0.39f, -1.41f, 0.0f)
                lineTo(12.0f, 15.41f)
                lineToRelative(-4.88f, 4.88f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(4.12f, -4.12f)
                curveToRelative(0.75f, -0.75f, 1.17f, -1.77f, 1.17f, -2.83f)
                verticalLineToRelative(-5.1f)
                lineToRelative(-0.88f, 0.88f)
                curveTo(9.73f, 8.1f, 9.1f, 8.1f, 8.71f, 7.71f)
                close()
            }
        }
        return mergeCache!!
    }

private var mergeCache: ImageVector? = null

val MergeIcons.CallSplit: ImageVector
    get() {
        if (callSplitCache != null) {
            return callSplitCache!!
        }
        callSplitCache = materialIcon(name = "MergeIcons.CallSplit", autoMirror = true) {
            materialPath {
                moveTo(14.85f, 4.85f)
                lineToRelative(1.44f, 1.44f)
                lineToRelative(-2.88f, 2.88f)
                lineToRelative(1.42f, 1.42f)
                lineToRelative(2.88f, -2.88f)
                lineToRelative(1.44f, 1.44f)
                curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
                verticalLineTo(4.5f)
                curveToRelative(0.0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
                horizontalLineToRelative(-4.29f)
                curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.36f, 0.85f)
                close()
                moveTo(8.79f, 4.0f)
                horizontalLineTo(4.5f)
                curveToRelative(-0.28f, 0.0f, -0.5f, 0.22f, -0.5f, 0.5f)
                verticalLineToRelative(4.29f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineTo(6.29f, 7.7f)
                lineTo(11.0f, 12.4f)
                verticalLineTo(19.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-7.0f)
                curveToRelative(0.0f, -0.26f, -0.11f, -0.52f, -0.29f, -0.71f)
                lineToRelative(-5.0f, -5.01f)
                lineToRelative(1.44f, -1.44f)
                curveToRelative(0.31f, -0.3f, 0.09f, -0.84f, -0.36f, -0.84f)
                close()
            }
        }
        return callSplitCache!!
    }

private var callSplitCache: ImageVector? = null
