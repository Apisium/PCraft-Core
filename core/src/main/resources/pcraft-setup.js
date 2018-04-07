'use strict'
const bufferFill = require('buffer-fill')

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
  const b = Buffer
  global.Buffer = Object.assign(
    function () { return b.apply(null, arguments) },
    b,
    { alloc, allocUnsafe, from: require('buffer-from') }
  )
  try {
    return require(opts.pkg.main || 'pcraft').__setup(opts)
  } catch (e) {
    if (e && e.stack) console.error(e.stack.replace(/\\\\n/g, '\n'))
  }
}

function alloc (size, fill, encoding) {
  if (typeof size !== 'number') throw new TypeError('"size" argument must be a number')
  if (size < 0) throw new RangeError('"size" argument must not be negative')
  const buffer = allocUnsafe(size)
  if (size === 0) return buffer
  if (fill === undefined) return bufferFill(buffer, 0)
  return bufferFill(buffer, fill, typeof encoding === 'string' ? encoding : undefined)
}
function allocUnsafe (size) {
  if (typeof size !== 'number') throw new TypeError('"size" argument must be a number')
  if (size < 0) throw new RangeError('"size" argument must not be negative')
  return new Buffer(size)
}
