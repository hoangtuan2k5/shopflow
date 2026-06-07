import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

export interface ApiClientProblem {
  message: string
  status?: number
  details?: unknown
}

export class ApiClientError extends Error {
  readonly status?: number
  readonly details?: unknown

  constructor(problem: ApiClientProblem) {
    super(problem.message)
    this.name = 'ApiClientError'
    this.status = problem.status
    this.details = problem.details
  }
}

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

export const httpClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    Accept: 'application/json',
  },
  timeout: 10_000,
})

httpClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<unknown>) => {
    throw toApiClientError(error)
  },
)

export async function request<TData>(config: AxiosRequestConfig): Promise<TData> {
  const response = await httpClient.request<TData>(config)

  return response.data
}

function toApiClientError(error: AxiosError<unknown>) {
  if (error.response) {
    return new ApiClientError({
      details: error.response.data,
      message: `API request failed with status ${error.response.status}.`,
      status: error.response.status,
    })
  }

  return new ApiClientError({
    message: error.message || 'API request failed before receiving a response.',
  })
}
