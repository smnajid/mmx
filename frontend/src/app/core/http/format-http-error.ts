/** Extract API error message from HttpClient failure shapes. */
export function formatHttpError(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'error' in err) {
    const body = (err as { error?: { message?: string } }).error;
    if (body?.message) {
      return body.message;
    }
  }
  return fallback;
}
