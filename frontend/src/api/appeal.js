import request from '../utils/request'

export function createAppeal(data) {
  return request({ url: '/appeals', method: 'post', data })
}

export function getMyAppeals() {
  return request({ url: '/appeals/my', method: 'get' })
}

export function getAllAppeals() {
  return request({ url: '/appeals', method: 'get' })
}

export function getPendingAppeals() {
  return request({ url: '/appeals/pending', method: 'get' })
}

export function handleAppeal(id, data) {
  return request({ url: `/appeals/${id}/handle`, method: 'put', data })
}
