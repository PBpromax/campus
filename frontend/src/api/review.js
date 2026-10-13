import request from '../utils/request'

export function createReview(data) {
  return request({ url: '/reviews', method: 'post', data })
}

export function getReviewsByDemand(demandId) {
  return request({ url: `/reviews/demand/${demandId}`, method: 'get' })
}

export function getReviewsByUser(userId) {
  return request({ url: `/reviews/user/${userId}`, method: 'get' })
}
