import SwiftUI

@main struct SZUNetworkGuardianApp: App {
    init() { BackgroundScheduler.register() }
    var body: some Scene { WindowGroup { ContentView() } }
}
