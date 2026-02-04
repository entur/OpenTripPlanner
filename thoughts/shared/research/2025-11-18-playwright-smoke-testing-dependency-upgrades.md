---
date: 2025-11-18 07:40:16 +0100
researcher: testower
git_commit: 61c0adea7b37764d564928d05eb06fc6eb3ee8af
branch: renovate/debug-ui-dependencies-(non-major)
repository: entur/OpenTripPlanner
topic: "How can we use Playwright for smoke testing frontend dependency upgrade PRs using Entur's backends"
tags: [research, codebase, testing, playwright, debug-client, renovate, smoke-testing, e2e]
status: complete
last_updated: 2025-11-18
last_updated_by: testower
---

# Research: Playwright Smoke Testing for Frontend Dependency Upgrade PRs

**Date**: 2025-11-18 07:40:16 +0100
**Researcher**: testower
**Git Commit**: 61c0adea7b37764d564928d05eb06fc6eb3ee8af
**Branch**: renovate/debug-ui-dependencies-(non-major)
**Repository**: entur/OpenTripPlanner

## Research Question

How can we use Playwright for smoke testing frontend dependency upgrade PRs using Entur's backends (e.g., `https://api.dev.entur.io/journey-planner/v3/graphql`, `https://otp2debug.dev.entur.org/otp/debug/vectortiles/style.json`, `https://otp2debug.dev.entur.org/graphiql?flavor=transmodel`)?

## Summary

The OTP debug client currently uses Vitest with React Testing Library for unit testing but lacks end-to-end (E2E) smoke testing capabilities. Playwright can be integrated to provide smoke testing for Renovate dependency upgrade PRs by:

1. **Running against Entur's development backends** using the existing `.env.development.local` configuration
2. **Executing critical user journeys** (trip planning, map interaction, result visualization)
3. **Integrating into the GitHub Actions workflow** (`debug-client.yml`) that already runs on dependency PRs
4. **Using the built production output** to test the actual bundled code that will be deployed

The debug client has a simple architecture (single-page React app with GraphQL API integration and MapLibre map visualization) that is well-suited for Playwright smoke testing. The current test coverage is minimal (only 2 unit test files), making E2E smoke tests a valuable addition for catching integration issues during dependency upgrades.

## Detailed Findings

### Current Testing Infrastructure

#### Unit Testing Setup
**Location**: `client/vite.config.ts:13-15`, `client/package.json:9-10`

The debug client uses:
- **Vitest 4.0.10** as the test runner
- **React Testing Library 16.3.0** for component testing
- **jsdom 27.2.0** for DOM simulation
- Test environment configured in `vite.config.ts` with `environment: 'jsdom'`
- Tests run from `src/` directory via `vitest --root src/`
- Coverage generated via `vitest run --root src/ --coverage`

#### Existing Test Files
**Locations**:
- `client/src/components/SearchBar/SearchBar.test.tsx:1-15`
- `client/src/components/ItineraryList/ItineraryList.test.tsx:1-2397`

Current test coverage is minimal:
- Only 2 test files exist in the entire client codebase
- Both follow a "renders without crashing" smoke test pattern
- No interaction testing, no API mocking, no assertions beyond implicit render success
- SearchBar test uses simple mock data with coordinates
- ItineraryList test includes a large 2383-line fixture with real GraphQL response data

#### CI/CD Integration
**Location**: `.github/workflows/debug-client.yml:44-49`

The `debug-client` workflow:
- Triggers on push to `dev-2.x` and PRs when `client/**` files change
- Uses Node.js 22
- Runs `npm ci`, `npm run build`, and `npm run coverage` sequentially
- Currently runs **after** the build completes
- No E2E or integration testing exists

### Environment Configuration for Testing Against Entur Backends

#### Development Environment Files
**Locations**:
- `client/.env:1-4` - Production config with relative paths
- `client/.env.development:1-4` - Local development with `localhost:8080`
- `client/.env.development.local:1-3` - **Entur development backends**

The `.env.development.local` file contains the target backends for smoke testing:
```
VITE_API_URL=https://api.dev.entur.io/journey-planner/v3/graphql
VITE_DEBUG_STYLE_URL=https://otp2debug.dev.entur.org/otp/debug/vectortiles/style.json
VITE_GRAPHIQL_URL=https://otp2debug.dev.entur.org/graphiql?flavor=transmodel
```

#### Environment Resolution
**Location**: `client/src/util/getApiUrl.ts:1-13`

The application reads environment variables via `import.meta.env.VITE_API_URL`:
- Tests if the URL is absolute at line 7
- Returns as-is if valid absolute URL at line 8
- Otherwise prepends `window.origin` at line 10
- This allows flexible configuration for different environments

**Testing Implication**: Playwright can use `.env.development.local` to test against real Entur backends without code changes.

### Debug Client Application Structure

#### Application Architecture
**Location**: `client/src/screens/App.tsx:1-94`

The debug client is a single-page React application with four main sections:

1. **Logo Section** (lines 37-38) - OTP logo and server info
2. **Input Fields Section** (lines 40-46) - Search form with from/to locations, date/time, modes
3. **Trip Section with Sidebar** (lines 48-73) - Three tabbed panels:
   - Tab 0: Itinerary results list
   - Tab 1: Advanced arguments editor (dynamic GraphQL parameter form)
   - Tab 2: Raw JSON view of arguments
4. **Map Section** (lines 75-82) - MapLibre GL map with route visualization

#### Key User Journeys for Smoke Testing

Based on the application structure, critical smoke test scenarios include:

**Journey 1: Basic Trip Planning**
- Enter from/to locations via text input or map click (`InputFieldsSection.tsx:32-46`)
- Select date/time (`DateTimeInputField.tsx`)
- Select transit modes (`TransitModeSelect.tsx`)
- Click "Route" button (`InputFieldsSection.tsx:68-78`)
- Verify itinerary results appear (`ItineraryListContainer.tsx:60-97`)
- Verify map shows route polylines (`MapView.tsx:128-134`)

**Journey 2: Map Interaction**
- Double-click map to set from/to locations (`MapView.tsx:41` with `useMapDoubleClick` hook)
- Right-click context menu to set locations (`MapView.tsx:103-105`)
- Verify navigation markers appear (`MapView.tsx:120-125`)
- Verify map style loads from `VITE_DEBUG_STYLE_URL` (`MapView.tsx:23`)

**Journey 3: Result Interaction**
- Expand/collapse itinerary accordion items (`ItineraryListContainer.tsx:62-72`)
- Select up to 2 itineraries for comparison (max 2 allowed at lines 62-72)
- View detailed leg information (`ItineraryDetails.tsx`)
- Compare selected itineraries (`ItineraryCompareDialog.tsx`)

**Journey 4: Advanced Features**
- Switch sidebar tabs (`Sidebar.tsx:14-25`)
- Modify trip arguments (`TripQueryArguments.tsx:1-399`)
- View raw JSON arguments (`ViewArgumentsRaw.tsx:29`)
- Use GraphiQL route button (`GraphiQLRouteButton.tsx`)

#### GraphQL Integration Points
**Locations**:
- `client/src/hooks/useTripQuery.ts:1-60` - Main trip query execution
- `client/src/hooks/useServerInfo.ts:1-32` - Server metadata fetch
- `client/src/static/query/tripQuery.tsx:1-50` - Generated query definition

The application makes two primary GraphQL queries:
1. **Server Info Query** (line `useServerInfo.ts:25`) - Fetches on mount, gets server version and timezone
2. **Trip Query** (`useTripQuery.ts:33`) - Executes when user clicks "Route" button, requires from/to coordinates

**Testing Implication**: Smoke tests need to wait for these network requests to complete.

#### Build Output Structure
**Location**: `client/output/index.html:1-14`

Production build creates:
- Single bundled JavaScript file: `/assets/index-CZuvhgZE.js` (line 8)
- Single bundled CSS file: `/assets/index--BYSqEed.css` (line 9)
- Hashed asset filenames for cache busting
- Self-contained output in `client/output/` directory (configured at `vite.config.ts:9`)

**Testing Implication**: Playwright can test against the built production output by serving the `output/` directory.

### Renovate Configuration for Debug Client

#### Dependency Update Schedule
**Location**: `renovate.json5:31-36`

Debug client dependencies are managed by Renovate:
```json5
{
  "matchFiles": ["client/package.json"],
  "matchUpdateTypes": ["patch", "minor"],
  "groupName": "Debug UI dependencies (non-major)",
  "schedule": ["after 6pm on the 17th day of the month"],
  "reviewers": ["testower"],
  "postUpdateOptions": ["npmInstallTwice"]
}
```

**Key aspects**:
- Non-major updates (patch/minor) are grouped into a single PR monthly
- Scheduled for the 17th of each month after 6pm
- `npmInstallTwice` handles peer dependency resolution issues
- Reviewer: testower
- Major updates handled separately (lines 39-42) with same reviewer

#### Existing PR Labels
**Location**: `renovate.json5:7-8`

All Renovate PRs get the `+Skip Changelog` label automatically. Additional labels can be configured for specific package groups.

**Testing Implication**: Playwright smoke tests could be triggered based on the presence of Renovate labels or by detecting dependency-only changes.

### GitHub Actions Workflow Current State

#### Debug Client Workflow
**Location**: `.github/workflows/debug-client.yml:1-94`

The workflow currently:
1. **Triggers** on push/PR to `dev-2.x` when `client/**` files change (lines 4-13)
2. **Runs only for opentripplanner org** (`if: github.repository_owner == 'opentripplanner'` at line 21)
3. **Environment**: Ubuntu latest, Node 22, 20-minute timeout (lines 22-23, 38-39)
4. **Build steps** (lines 44-49):
   ```bash
   npm ci
   npm run build -- --base https://www.opentripplanner.org/debug-client-assets/${VERSION}/
   npm run coverage
   ```
5. **Deployment** (lines 52-93): Deploys to `debug-client-assets` repo on push to `dev-2.x`

**Current flow**: Build → Test (unit) → Deploy

**Testing Implication**: Playwright smoke tests should run after build but before deployment, allowing failures to block deployment.

### Playwright Integration Patterns

Based on the research findings, here are the existing patterns that would support Playwright integration:

#### Pattern 1: Development Server Testing
Current development command (`package.json:7`):
```json
"dev": "vite"
```

This starts Vite dev server on `http://localhost:5173/` with hot reload. Playwright could:
- Use `webServer` configuration to auto-start dev server
- Test against unbundled code with source maps
- Faster test execution due to dev server's optimization
- **Trade-off**: Tests dev build, not production build

#### Pattern 2: Production Build Testing
Current build command (`package.json:8`):
```json
"build": "tsc && vite build"
```

This creates production output in `client/output/`. Playwright could:
- Use `webServer` with a static file server (e.g., `vite preview` or `http-server`)
- Test actual production bundle that will be deployed
- Catch build-specific issues (minification, bundling, code splitting)
- **Trade-off**: Slower build step, but more realistic

#### Pattern 3: Preview Server Testing
Current preview command (`package.json:14`):
```json
"preview": "vite preview"
```

This serves the built output from `client/output/`. Playwright could:
- Use `npm run build && npm run preview` as `webServer` command
- Test production build served via Vite's preview server
- **Best of both worlds**: Tests production code with convenient tooling

### No Existing E2E Testing Infrastructure

**Search results**: No Playwright, Cypress, Puppeteer, or similar E2E testing tools found in the client codebase.

**Implications**:
- Clean slate for Playwright integration
- No conflicting test frameworks
- No existing E2E test patterns to follow or conflict with
- Need to establish conventions for:
  - Test file naming (e.g., `*.spec.ts` or `*.e2e.ts`)
  - Test file location (e.g., `client/e2e/` or `client/tests/e2e/`)
  - Page object pattern vs. inline selectors
  - Test data fixtures

## Current Implementation Documentation

### Environment Variable Loading
**Location**: `client/vite.config.ts:5-6`, `client/src/util/getApiUrl.ts:1-13`

Vite loads environment variables based on mode:
- Default mode reads `.env` and `.env.development`
- Mode can be overridden via `--mode` flag
- `.env.development.local` takes precedence over `.env.development` in development mode
- Variables prefixed with `VITE_` are exposed to client code via `import.meta.env`

**Current behavior**:
- Running `npm run dev` loads `.env.development.local` (Entur backends)
- Running `npm run build` loads `.env` (relative paths for production)

### API Request Flow
**Location**: `client/src/hooks/useTripQuery.ts:19-46`

Current trip query execution:
1. User fills in from/to locations and parameters
2. User clicks "Route" button triggering `onRoute()` callback (`InputFieldsSection.tsx:68-78`)
3. `useTripQuery` callback executes at line 19-46:
   - Validates variables exist (line 24)
   - Sets loading state (line 25)
   - Creates pruned query and variables (lines 29-31)
   - Executes GraphQL request via `graphql-request` library (line 33)
   - Updates data state with results (implied in success path)
   - Handles errors and updates error state (lines 34-38)
   - Clears loading state (line 39)
4. Auto-execution on location changes (lines 48-53) triggers same flow

**Network characteristics**:
- Uses `graphql-request` library for all GraphQL calls
- No request retry logic visible
- No timeout configuration visible (relies on fetch defaults)
- No loading skeleton during initial fetch

**Testing implication**: Playwright tests need to wait for loading state to clear before asserting results.

### Map Loading Flow
**Location**: `client/src/components/MapView/MapView.tsx:1-161`

Current map initialization:
1. Map style URL loaded from `import.meta.env.VITE_DEBUG_STYLE_URL` (line 23)
2. MapLibre GL initializes with style (lines 96-118)
3. `onLoad` callback executes (line 38):
   - Pan to world envelope if zoomed out (lines 65-75)
   - Add attribution control (lines 77-80)
   - Find and set interactive layers (lines 86-87)
4. Map becomes interactive with click handlers (line 110)

**Network characteristics**:
- Style JSON fetched from remote URL
- Vector tiles streamed as user pans/zooms
- No explicit loading error handling visible

**Testing implication**: Playwright tests need to wait for map load event before interacting with map.

## Architecture Documentation

### Component Hierarchy

```
App (src/screens/App.tsx)
├── Logo Section
│   └── LogoSection (SearchBar/LogoSection.tsx)
│       └── ServerInfoTooltip (SearchBar/ServerInfoTooltip.tsx)
├── Input Fields Section
│   └── InputFieldsSection (SearchBar/InputFieldsSection.tsx)
│       ├── LocationInputField (×2 for from/to)
│       ├── SwapLocationsButton
│       ├── DepartureArrivalSelect
│       ├── DateTimeInputField
│       ├── NumTripPatternsInput
│       ├── SearchWindowInput
│       ├── AccessSelect
│       ├── TransitModeSelect
│       ├── EgressSelect
│       ├── DirectModeSelect
│       ├── WheelchairAccessibleCheckBox
│       ├── ItineraryFilterDebugSelect
│       └── GraphiQLRouteButton
├── Trip Section (Sidebar)
│   ├── Sidebar (SearchInput/Sidebar.tsx)
│   │   ├── Tab 0: ItineraryListContainer (ItineraryList/ItineraryListContainer.tsx)
│   │   │   ├── ItineraryPaginationControl
│   │   │   ├── ErrorDisplay
│   │   │   ├── NoResultsDisplay
│   │   │   └── Bootstrap Accordion
│   │   │       └── Accordion Items (one per itinerary)
│   │   │           ├── ItineraryHeaderContent (header)
│   │   │           │   └── ItineraryHeaderLegContent (×N for each leg)
│   │   │           └── ItineraryDetails (body)
│   │   │               └── ItineraryLegDetails (×N for each leg)
│   │   ├── Tab 1: TripQueryArguments (SearchInput/TripQueryArguments.tsx)
│   │   │   └── Dynamic form inputs based on GraphQL schema
│   │   └── Tab 2: ViewArgumentsRaw (SearchInput/ViewArgumentsRaw.tsx)
│   └── ItineraryCompareDialog (modal, triggered by pagination control)
└── Map Section
    └── MapView (MapView/MapView.tsx)
        ├── NavigationMarkers (MapView/NavigationMarkers.tsx)
        ├── LegLines (MapView/LegLines.tsx)
        ├── LayerControl (MapView/LayerControl.tsx)
        ├── RightMenu (MapView/RightMenu.tsx)
        ├── ContextMenuPopup (MapView/ContextMenuPopup.tsx)
        ├── GeometryPropertyPopup (MapView/GeometryPropertyPopup.tsx)
        └── FeatureSelectPopup (MapView/FeatureSelectPopup.tsx)
```

### State Management Pattern

The application uses React hooks for state management (no Redux, MobX, or similar):

**Global State** (`App.tsx:18-25`):
- `serverInfo` - Server metadata (version, timezone)
- `tripQueryVariables` - Current trip parameters (synced to URL query string)
- `comparisonSelectedIndexes` - Indexes of itineraries selected for comparison
- `showCompareDialog` - Boolean for comparison modal visibility
- `tripQueryResult` - GraphQL response data
- `selectedTripPatternIndexes` - Indexes of expanded accordion items (max 2)
- `expandedArguments` - Set of expanded argument paths in advanced editor

**Context Providers**:
- `TimeZoneContext` (`hooks/TimeZoneContext.ts`) - Shared timezone from server or browser
- `TripSchemaProvider` (`SearchInput/TripSchemaProvider.tsx`) - GraphQL schema for dynamic form

**URL State Sync** (`hooks/useTripQueryVariables.ts:16-20`):
- Trip variables encoded as JSON in `?variables=` query parameter
- Updated on every variable change via `history.pushState()`
- Allows bookmarking and sharing specific trip queries

### Key Selectors for Testing

Based on the component structure, here are stable selectors for Playwright:

**Input elements**:
- From location input: Look for input with "From" label or placeholder
- To location input: Look for input with "To" label or placeholder
- Date/time input: `input[type="datetime-local"]`
- Route button: Button with text "Route" (or with loading spinner)

**Results elements**:
- Itinerary accordion: `.accordion` or `[data-bs-toggle="collapse"]`
- Accordion items: `.accordion-item`
- Active/selected items: `.accordion-item` with `background: #ffc0cb` (pink)
- Error display: Component with error message text
- No results display: Component with "No results" text

**Map elements**:
- Map container: `.maplibregl-map` or `.mapboxgl-map`
- Navigation markers: `.maplibregl-marker` (start/end flags)
- Layer control: Component in right menu with layer checkboxes
- Context menu popup: Popup with "Set as From" / "Set as To" options

**Sidebar elements**:
- Tab buttons: Buttons with icons (Trip, Filter, JSON)
- Active tab: Button with active/selected styling
- Arguments editor: Form inputs in Tab 1
- Raw JSON view: `<pre>` element in Tab 2

## Code References

### Key Files for Testing Integration

**Configuration Files**:
- `client/vite.config.ts:1-17` - Vite and Vitest configuration
- `client/package.json:1-62` - Scripts and dependencies
- `client/.env.development.local:1-3` - Entur backend URLs
- `client/tsconfig.json:1-26` - TypeScript configuration

**Application Entry Points**:
- `client/index.html:10-11` - HTML root and script reference
- `client/src/main.tsx:1-12` - React render entry point
- `client/src/screens/App.tsx:1-94` - Main application component

**Testing-Relevant Components**:
- `client/src/hooks/useTripQuery.ts:19-46` - Trip query execution logic
- `client/src/hooks/useServerInfo.ts:23-28` - Server info fetch
- `client/src/components/SearchBar/InputFieldsSection.tsx:68-78` - Route button
- `client/src/components/ItineraryList/ItineraryListContainer.tsx:60-97` - Results display
- `client/src/components/MapView/MapView.tsx:96-118` - Map initialization

**CI/CD Integration**:
- `.github/workflows/debug-client.yml:44-49` - Build and test steps
- `.github/workflows/debug-client.yml:52-93` - Deployment steps
- `renovate.json5:31-42` - Debug client dependency update rules

### Current Test Files

- `client/src/components/SearchBar/SearchBar.test.tsx:1-15` - Simple smoke test
- `client/src/components/ItineraryList/ItineraryList.test.tsx:1-2397` - Smoke test with fixture data

## Related Research

No existing research documents found in `thoughts/shared/research/` about testing strategies, E2E testing, or Playwright integration.

Related topics that might benefit from future research:
- Visual regression testing for map rendering and itinerary display
- Load testing against Entur's production APIs
- Accessibility testing with axe-core or similar tools
- Mobile device testing (responsive behavior)

## Open Questions

### Technical Questions

1. **Environment Configuration**:
   - Should Playwright use `.env.development.local` directly, or should there be a separate `.env.test` file?
   - How to handle API rate limiting from Entur's backends during CI runs?
   - Should tests use real Entur backends or mock GraphQL responses?

2. **Test Scope**:
   - Which user journeys are most critical for smoke testing?
   - Should tests validate map tile loading or just check for map container presence?
   - How deep should interaction testing go (e.g., test every mode combination)?
   - Should tests verify GraphiQL link generation?

3. **Test Data**:
   - Use hardcoded coordinates (current test pattern) or fixture data?
   - How to ensure test locations remain valid in Entur's network?
   - Should tests use date/time from fixtures or dynamic "now + 1 hour"?

4. **Performance**:
   - What timeout values are appropriate for API calls to Entur backends?
   - Should Playwright run in parallel or serial for dependency PRs?
   - How long should smoke test suite take to avoid slowing CI?

5. **CI Integration**:
   - Should smoke tests run on every PR or only Renovate PRs?
   - Should smoke tests block deployment or just report failures?
   - How to handle flaky tests due to backend issues?
   - Should tests run against `localhost` preview or deployed URL?

### Process Questions

1. **Test Maintenance**:
   - Who is responsible for maintaining Playwright tests?
   - How to handle test failures - rollback dependency update or fix test?
   - Should test failures require manual review before merge?

2. **Coverage Goals**:
   - What percentage of user journeys should smoke tests cover?
   - Should smoke tests replace or complement unit tests?
   - When should new smoke tests be added?

3. **Reporting**:
   - Should Playwright generate visual reports (screenshots, videos)?
   - Where should test artifacts be stored (GitHub Actions artifacts)?
   - Should test results be posted to PRs as comments?

## How Playwright Can Be Used for Smoke Testing

### Integration Approach

Based on the current infrastructure, here's how Playwright can be integrated:

#### 1. Package Installation

Add Playwright to dev dependencies:
```json
{
  "devDependencies": {
    "@playwright/test": "^1.40.0"
  }
}
```

Add scripts to `package.json`:
```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:headed": "playwright test --headed",
    "test:e2e:ui": "playwright test --ui",
    "test:e2e:report": "playwright show-report"
  }
}
```

#### 2. Configuration File

Create `client/playwright.config.ts`:
```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // Run serially for stability with external API
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI
    ? [['html'], ['github']]
    : [['html'], ['list']],
  use: {
    baseURL: 'http://localhost:4173', // Vite preview server
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run preview',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
```

**Key decisions**:
- Tests in `client/e2e/` directory
- Serial execution for stability with real API
- 2 retries in CI for flakiness tolerance
- Uses `npm run preview` to serve built output
- Single browser (Chromium) for speed
- Traces and screenshots on failure

#### 3. Test File Structure

Create `client/e2e/smoke.spec.ts`:
```typescript
import { test, expect } from '@playwright/test';

test.describe('Smoke Tests - Basic Trip Planning', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for server info to load
    await page.waitForLoadState('networkidle');
  });

  test('should load application and display UI elements', async ({ page }) => {
    // Verify logo and server info
    await expect(page.locator('img[alt*="OTP"]')).toBeVisible();

    // Verify input fields
    await expect(page.getByLabel(/from/i)).toBeVisible();
    await expect(page.getByLabel(/to/i)).toBeVisible();

    // Verify map container
    await expect(page.locator('.maplibregl-map')).toBeVisible();
  });

  test('should execute trip query and display results', async ({ page }) => {
    // Fill in from location (Oslo coordinates)
    const fromInput = page.getByLabel(/from/i);
    await fromInput.fill('60.13776, 9.795206');

    // Fill in to location (Lillehammer coordinates)
    const toInput = page.getByLabel(/to/i);
    await toInput.fill('61.11495, 10.46636');

    // Click route button
    const routeButton = page.getByRole('button', { name: /route/i });
    await routeButton.click();

    // Wait for results to load
    await page.waitForSelector('.accordion-item', { timeout: 30000 });

    // Verify at least one itinerary is displayed
    const itineraries = page.locator('.accordion-item');
    await expect(itineraries).toHaveCount(await itineraries.count());
    expect(await itineraries.count()).toBeGreaterThan(0);
  });

  test('should interact with map', async ({ page }) => {
    // Wait for map to load
    await page.waitForSelector('.maplibregl-canvas');

    // Verify map is interactive
    const map = page.locator('.maplibregl-map');
    await expect(map).toBeVisible();

    // Double-click to set from location
    await map.dblclick({ position: { x: 200, y: 200 } });

    // Verify marker appears
    await expect(page.locator('.maplibregl-marker')).toBeVisible();
  });
});
```

**Key patterns**:
- Use semantic locators (`getByLabel`, `getByRole`) where possible
- Wait for `networkidle` to ensure API calls complete
- Use timeout overrides for slow API calls (30s)
- Assert on count and visibility, not exact content
- Test critical path only (from/to input → route → results)

#### 4. GitHub Actions Integration

Update `.github/workflows/debug-client.yml`:
```yaml
- name: Build debug client
  working-directory: client
  run: |
    npm ci
    npm run build -- --base https://www.opentripplanner.org/debug-client-assets/${VERSION}/
    npm run coverage

- name: Run Playwright smoke tests
  working-directory: client
  run: |
    npx playwright install --with-deps chromium
    npm run test:e2e
  env:
    CI: true

- name: Upload Playwright report
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: playwright-report
    path: client/playwright-report/
    retention-days: 30
```

**Key decisions**:
- Run after unit tests and coverage
- Install Chromium browser with system dependencies
- Use production build (already built in previous step)
- Upload reports only on failure to save storage
- 30-day retention for debugging

#### 5. Environment Variables for Testing

Option A: Use existing `.env.development.local` (simplest):
- Playwright's `webServer` will load this automatically via Vite
- Tests will use Entur's dev backends
- No additional configuration needed

Option B: Create `.env.test` (more explicit):
```
VITE_API_URL=https://api.dev.entur.io/journey-planner/v3/graphql
VITE_DEBUG_STYLE_URL=https://otp2debug.dev.entur.org/otp/debug/vectortiles/style.json
VITE_GRAPHIQL_URL=https://otp2debug.dev.entur.org/graphiql?flavor=transmodel
```

Update `playwright.config.ts`:
```typescript
webServer: {
  command: 'vite preview --mode test',
  // ...
}
```

**Recommendation**: Start with Option A for simplicity, move to Option B if test-specific config is needed.

### Test Coverage Strategy

**Critical Path (P0 - Must test)**:
1. Application loads without errors
2. UI elements render correctly
3. Trip query executes and returns results
4. Results display in accordion
5. Map initializes and displays

**Important Features (P1 - Should test)**:
1. Map interaction (double-click, context menu)
2. Location swap button
3. Mode selection dropdowns
4. Itinerary expansion/collapse
5. Sidebar tab navigation

**Nice to Have (P2 - Could test)**:
1. Advanced arguments editor
2. Itinerary comparison dialog
3. GraphiQL link generation
4. Pagination controls
5. Raw JSON view

**Out of Scope**:
1. Visual regression testing (requires dedicated tooling)
2. Accessibility testing (should be separate suite)
3. Performance testing (should be separate suite)
4. Cross-browser testing (focus on Chromium for smoke tests)

### Expected Test Execution Time

Based on the application's behavior:
- Application load: ~2-3 seconds
- Server info fetch: ~1-2 seconds
- Trip query execution: ~5-10 seconds (depends on backend)
- Map tile loading: ~2-3 seconds
- Total per test: ~10-20 seconds

With 3 smoke tests:
- **Total suite runtime: ~30-60 seconds**

This is acceptable for CI execution and won't significantly slow down Renovate PRs.

### Handling Backend Availability

Since tests depend on Entur's live backends:

**Retry Strategy**:
- Configure 2 retries in CI (`retries: 2`)
- Fail only if all 3 attempts fail
- Use generous timeouts (30s for GraphQL queries)

**Timeout Configuration**:
```typescript
test('should execute trip query', async ({ page }) => {
  test.setTimeout(60000); // 60s for this test
  // ... test code
});
```

**Error Handling**:
- Screenshot on failure for debugging
- Trace recording on first retry
- HTML report with network logs

### Maintenance Considerations

**When Playwright tests fail on Renovate PRs**:
1. Check if backend is experiencing issues (Entur status page)
2. Review Playwright report artifacts in GitHub Actions
3. If test is flaky, increase timeout or add explicit waits
4. If dependency broke functionality, rollback or fix code
5. If test is outdated, update test to match new UI

**When to update tests**:
- When UI components are refactored (update selectors)
- When user flows change (update test steps)
- When new critical features are added (add new tests)
- When backend contracts change (update assertions)

**Test ownership**:
- Assigned to testower (same as debug client dependencies)
- Should be reviewed with dependency PRs
- Can be skipped with `[skip e2e]` in commit message if needed

### Benefits for Dependency Upgrades

Playwright smoke tests provide value for Renovate PRs by catching:
1. **Breaking UI changes** - Dependency upgrades that break component rendering
2. **API integration issues** - Changes that break GraphQL request handling
3. **Map library issues** - MapLibre or related library breaking changes
4. **Build configuration problems** - Vite or build tool updates causing runtime errors
5. **TypeScript incompatibilities** - Type errors that pass `tsc` but fail at runtime
6. **Polyfill issues** - Dependency updates requiring new polyfills
7. **CSS/styling issues** - Bootstrap or style-related breaking changes

### Integration with Renovate

**Option 1: Run on all Renovate PRs**:
- Simple, consistent
- Catches issues immediately
- Small overhead (~1 minute per PR)

**Option 2: Run only on grouped PRs**:
- Only run on "Debug UI dependencies (non-major)" PRs
- Skip individual dependency PRs
- Faster iteration on single-dep PRs

**Option 3: Required status check**:
- Make Playwright check required in GitHub branch protection
- Blocks merge if tests fail
- Ensures no broken builds reach `dev-2.x`

**Recommendation**: Start with Option 1, evaluate if it causes too many false positives, then consider Option 2.

---

## Summary of Current State

The OpenTripPlanner debug client is a React-based SPA that currently lacks E2E testing infrastructure. The application's simple architecture, existing environment configuration for Entur backends, and CI/CD workflow make it well-suited for Playwright integration. Smoke tests can run against the production build served via Vite preview, testing the actual code that will be deployed. The tests would execute critical user journeys (trip planning, map interaction, result visualization) against real Entur APIs, providing confidence that dependency upgrades haven't broken core functionality.
