import { buildApp } from './app.ts'
import { loadConfig } from './config.ts'

const config = loadConfig()
const { app } = await buildApp({ config })

const shutdown = async (signal: string) => {
  app.log.info(`${signal} received, shutting down`)
  await app.close()
  process.exit(0)
}
process.on('SIGTERM', () => void shutdown('SIGTERM'))
process.on('SIGINT', () => void shutdown('SIGINT'))

await app.listen({ port: config.port, host: config.host })
app.log.info(
  `Pingly server ready (data: ${config.dataDir}, sms: ${config.sms.apiKey ? 'SMS.ir' : 'console'}, production: ${config.isProduction})`,
)
