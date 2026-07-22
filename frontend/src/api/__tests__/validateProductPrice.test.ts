import assert from 'node:assert/strict'
import test from 'node:test'
import { validateProductPrice } from '../validateProductPrice.ts'

test('accepts only non-negative whole VND catalog prices', () => {
  const product = { price: 699_000 }

  assert.equal(validateProductPrice(product), product)
  assert.throws(() => validateProductPrice({ price: 699_000.5 }), TypeError)
  assert.throws(() => validateProductPrice({ price: -1 }), TypeError)
  assert.throws(() => validateProductPrice({ price: Number.MAX_SAFE_INTEGER + 1 }), TypeError)
})
