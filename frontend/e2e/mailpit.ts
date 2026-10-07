// Mailpit helpers for e2e tests. Using Mailpit's REST API for magic-link
// extraction is far more reliable than scraping its UI iframe (which
// changes shape between releases).

const MAILPIT = 'http://localhost:8025'

export async function clearMailpit(): Promise<void> {
  await fetch(`${MAILPIT}/api/v1/messages`, { method: 'DELETE' })
}

export async function fetchMagicLink(
  recipient: string,
  opts: { attempts?: number; delayMs?: number } = {}
): Promise<string> {
  const attempts = opts.attempts ?? 60
  const delayMs  = opts.delayMs  ?? 500

  for (let n = 0; n < attempts; n++) {
    const search = await fetch(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${recipient}`)}&limit=1`
    )
    const data = await search.json() as { messages?: Array<{ ID: string }> }
    if (data.messages && data.messages.length > 0) {
      const id = data.messages[0].ID
      const full = await fetch(`${MAILPIT}/api/v1/message/${id}`)
        .then(r => r.json()) as { Text?: string; HTML?: string }
      const body = full.Text ?? full.HTML ?? ''
      const match = body.match(/http:\/\/localhost:3000\/auth\/magic-link\?token=[^\s"<]+/)
      if (match) return match[0]
    }
    await new Promise(r => setTimeout(r, delayMs))
  }
  throw new Error(`No magic link found for ${recipient} after ${(attempts * delayMs) / 1000}s`)
}

/** Wait for an email to [recipient] whose subject contains [subject]; returns its subject. */
export async function waitForEmail(
  recipient: string,
  subject: string,
  opts: { attempts?: number; delayMs?: number } = {}
): Promise<string> {
  const attempts = opts.attempts ?? 60
  const delayMs  = opts.delayMs  ?? 500
  const query = encodeURIComponent(`to:${recipient} subject:"${subject}"`)
  for (let n = 0; n < attempts; n++) {
    const data = await fetch(`${MAILPIT}/api/v1/search?query=${query}&limit=1`)
      .then(r => r.json()) as { messages?: Array<{ Subject: string }> }
    if (data.messages && data.messages.length > 0) return data.messages[0].Subject
    await new Promise(r => setTimeout(r, delayMs))
  }
  throw new Error(`No "${subject}" email for ${recipient} after ${(attempts * delayMs) / 1000}s`)
}
