import request from '../utils/request'

export function sendMessage(data) {
  return request({ url: '/messages', method: 'post', data })
}

export function getMessagesByDemand(demandId) {
  return request({ url: `/messages/demand/${demandId}`, method: 'get' })
}

export function getMyConversations() {
  return request({ url: '/messages/my', method: 'get' })
}
