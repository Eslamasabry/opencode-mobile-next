package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText

/** The motion-parts gallery's `_scene` (test/goldens/kit/kit_motion_parts_golden_test.dart): a caption above [content]. */
@Composable
fun GalleryScene(caption: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        KitText(caption, role = TypeRole.Label)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
