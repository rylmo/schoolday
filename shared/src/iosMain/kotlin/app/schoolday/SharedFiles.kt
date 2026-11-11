package app.schoolday

/**
 * Implemented by the Swift app: writes a file into the folder shared with the widgets (an app
 * group), and does nothing when that folder isn't available.
 */
interface SharedFiles {
    fun write(name: String, text: String)
}
