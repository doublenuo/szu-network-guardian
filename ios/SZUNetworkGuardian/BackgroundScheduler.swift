import BackgroundTasks

enum BackgroundScheduler {
    static let identifier = "com.weno.szuguardian.ios.refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            task.expirationHandler = { task.setTaskCompleted(success: false) }
            Task { @MainActor in
                let model = GuardianModel(); model.check(); task.setTaskCompleted(success: true)
            }
        }
    }

    static func schedule(after minutes: Int = 15) {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: TimeInterval(max(15, minutes) * 60))
        try? BGTaskScheduler.shared.submit(request)
    }
}
