'use strict'
module.exports = opts => {
  opts.pkg = JSON.parse(opts.pkg)
  try {
    if (opts.pkg.config.debug) {
      Object.defineProperty(process.env, 'NODE_ENV', { value: 'development' })
      Object.defineProperty(global, '__DEV__', { value: true })
    } else throw new Error('')
  } catch (e) {
    Object.defineProperty(process.env, 'NODE_ENV', { value: 'production' })
  }
  try {
    require('source-map-support').install()
  } catch (e) {}
  require('babel-polyfill')
  try {
    return require(opts.pkg.main || 'pcraft').__setup(opts)
  } catch (e) {
    if (e && e.stack) console.error(e.stack.replace(/\\\\n/g, '\n'))
  }
}
