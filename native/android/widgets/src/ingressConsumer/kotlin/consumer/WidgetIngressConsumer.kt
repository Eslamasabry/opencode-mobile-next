package consumer

import android.content.Intent
import io.github.eslamasabry.oc.android.widgets.WidgetActionAdmission
import io.github.eslamasabry.oc.android.widgets.WidgetActionExtras
import io.github.eslamasabry.oc.engine.attention.WidgetActionIngress
import io.github.eslamasabry.oc.engine.attention.WidgetActionRequest

// widgets-MAJOR-6: compiled separately, without test friend paths or a sibling Android module edge.
public fun ingress(admission: WidgetActionAdmission): WidgetActionIngress = admission

public fun request(intent: Intent): WidgetActionRequest? = WidgetActionExtras.read(intent)
