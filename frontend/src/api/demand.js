import request from '../utils/request'

export function createDemand(data) {
  return request({
    url: '/demands',
    method: 'post',
    data
  })
}

export function getDemandById(id) {
  return request({
    url: `/demands/${id}`,
    method: 'get'
  })
}

export function getAllDemands(params) {
  return request({
    url: '/demands',
    method: 'get',
    params
  })
}

export function getDemandsByStatus(status) {
  return request({
    url: `/demands/status/${status}`,
    method: 'get'
  })
}

export function getDemandsByCategory(category) {
  return request({
    url: `/demands/category/${category}`,
    method: 'get'
  })
}

export function getDemandsByPublisher(publisherId) {
  return request({
    url: `/demands/publisher/${publisherId}`,
    method: 'get'
  })
}

export function getDemandsByAcceptor(acceptorId) {
  return request({
    url: `/demands/acceptor/${acceptorId}`,
    method: 'get'
  })
}

export function acceptDemand(id) {
  return request({
    url: `/demands/${id}/accept`,
    method: 'put'
  })
}

export function completeDemand(id, data) {
  return request({
    url: `/demands/${id}/complete`,
    method: 'put',
    data
  })
}

export function confirmDemand(id) {
  return request({
    url: `/demands/${id}/confirm`,
    method: 'put'
  })
}

export function cancelDemand(id, data) {
  return request({
    url: `/demands/${id}/cancel`,
    method: 'put',
    data
  })
}

export function deleteDemand(id) {
  return request({
    url: `/demands/${id}`,
    method: 'delete'
  })
}
