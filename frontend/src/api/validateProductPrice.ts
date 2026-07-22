export function validateProductPrice<T extends { price: unknown }>(product: T): T {
  if (
    typeof product.price !== 'number' ||
    product.price < 0 ||
    !Number.isSafeInteger(product.price)
  ) {
    throw new TypeError('Catalog price must be a non-negative whole VND amount.')
  }

  return product
}
