# pm-order-creation-widget Specification

## MODIFIED Requirements

### Requirement: Widget Playground for development and demo

The mmx trader frontend SHALL include a route at `/dev/widget-playground` (available only in dev mode) that embeds the `OrderCreationWizardComponent` with a configuration panel and event log. The configuration panel SHALL offer a **live-contract picker** instead of a free-text contract field: the user selects an order type, the playground calls `GET /api/v1/order-creation/contracts?portfolioNumber={pf}&orderType={type}`, and selecting a contract sets the widget's `contractNumber` input (driving the OnCall lifecycle shortcut). Term contracts MAY be listed but are not selectable into the shortcut until Term lifecycle operations exist.

#### Scenario: Playground is accessible in dev mode

- **WHEN** the mmx frontend is running in development mode
- **THEN** navigating to `/dev/widget-playground` renders the playground with the widget

#### Scenario: Playground is not accessible in production

- **WHEN** the mmx frontend is built for production
- **THEN** the `/dev/widget-playground` route is not registered and returns 404

#### Scenario: Picker lists live contracts and drives the shortcut

- **WHEN** the user selects order type OnCall in the playground and the contracts endpoint returns CT-00042 for the configured portfolio
- **THEN** the picker lists CT-00042, and selecting it mounts the widget with `contractNumber` set to CT-00042 (entering the lifecycle shortcut)

#### Scenario: Events are logged in the playground

- **WHEN** the widget emits `orderReady` in the playground
- **THEN** the event payload is displayed as formatted JSON in the event log panel
