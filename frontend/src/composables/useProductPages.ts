import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useInfiniteQuery } from '@tanstack/vue-query'
import { getProducts } from '@/api/catalog'

export function useProductPages(query: MaybeRefOrGetter<string> = '') {
  return useInfiniteQuery({
    queryKey: computed(() => ['products', 'pages', toValue(query)]),
    queryFn: ({ pageParam, signal }) =>
      getProducts({ after: pageParam, q: toValue(query), signal }),
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: 1,
  })
}
