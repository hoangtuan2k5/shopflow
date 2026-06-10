import { request } from './httpClient'

export interface OpenApiInfo {
  title?: string
  description?: string
  version?: string
}

export interface OpenApiTag {
  name: string
  description?: string
}

export interface OpenApiDocument {
  openapi: string
  info: OpenApiInfo
  paths: Record<string, unknown>
  components?: Record<string, unknown>
  tags?: OpenApiTag[]
}

export function getOpenApiDocument() {
  return request<OpenApiDocument>({
    method: 'GET',
    url: '/v3/api-docs',
  })
}
