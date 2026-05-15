/** Integration state for EXECUTED rows — outbound Kafka handoff (FR-013). */
export enum HandoffStatus {
  PENDING = 'PENDING',
  PUBLISHED = 'PUBLISHED',
  FAILED = 'FAILED',
}
