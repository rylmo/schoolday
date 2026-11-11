import Shared
import WidgetKit

/// Lets the shared Kotlin code hand files to the widgets, and tells them to redraw.
final class GroupFiles: NSObject, SharedFiles {
    func write(name: String, text: String) {
        guard AppGroup.container != nil else { return }
        AppGroup.write(name, text)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
