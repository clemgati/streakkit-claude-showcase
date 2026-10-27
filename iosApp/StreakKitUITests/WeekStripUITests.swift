import XCTest

/// Interaction tests for the week strip.
///
/// This is the test that would have caught the bug it now guards. The cell is a `ZStack` whose
/// fill is `Color.clear` when a day is unchecked, and SwiftUI does not hit-test a clear fill —
/// so the tap target was the 1pt ring rather than the 32pt circle, and only for *unchecked*
/// days, which is the tap users actually make. `.contentShape(Circle())` fixed it.
///
/// Nothing else in the repository could have caught it: every other gate is a compile or a
/// pure-logic test, and an untappable control passes all of them.
///
/// The Android counterpart is `androidApp/src/test/.../WeekStripTest.kt`, which asserts the same
/// behaviour through the same accessibility identifiers.
final class WeekStripUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    /// The first cell reporting itself as not checked, or nil if the seeded week is full.
    private func firstUncheckedDay() -> XCUIElement? {
        let cells = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'week-day-'"))
        for index in 0 ..< cells.count {
            let cell = cells.element(boundBy: index)
            if cell.exists, cell.value as? String == "not checked" {
                return cell
            }
        }
        return nil
    }

    func testUncheckedDayIsTappableAcrossItsWholeCircle() {
        guard let cell = firstUncheckedDay() else {
            XCTFail("Seeded data should leave at least one unchecked day in the week strip")
            return
        }
        let identifier = cell.identifier
        XCTAssertEqual(cell.value as? String, "not checked", "precondition")

        // Tap the centre. With a clear fill and no contentShape, the centre is not hit-testable
        // and this tap lands on nothing — which is exactly how the bug presented.
        cell.tap()

        let flipped = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", identifier))
            .firstMatch
        XCTAssertTrue(
            flipped.waitForExistence(timeout: 2),
            "the cell should still be on screen after tapping it"
        )
        XCTAssertEqual(
            flipped.value as? String,
            "checked",
            "tapping the centre of an unchecked day must toggle it; if this fails the cell's "
                + "hit area has shrunk to its border again (missing .contentShape)"
        )
    }

    func testTappingTwiceReturnsToTheOriginalState() {
        guard let cell = firstUncheckedDay() else {
            XCTFail("Seeded data should leave at least one unchecked day")
            return
        }
        let identifier = cell.identifier

        cell.tap()
        let afterFirst = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", identifier)).firstMatch
        XCTAssertEqual(afterFirst.value as? String, "checked")

        afterFirst.tap()
        let afterSecond = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", identifier)).firstMatch
        XCTAssertEqual(afterSecond.value as? String, "not checked", "toggling is symmetric")
    }

    func testEveryDayCellExposesItsStateToVoiceOver() {
        let cells = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'week-day-'"))
        XCTAssertGreaterThanOrEqual(cells.count, 7, "at least one habit's full week should be visible")

        for index in 0 ..< min(cells.count, 7) {
            let value = cells.element(boundBy: index).value as? String
            XCTAssertTrue(
                value == "checked" || value == "not checked",
                "every cell must announce its state, got \(String(describing: value))"
            )
        }
    }
}
