
import request from '../utils/request'

export function register(data) {
  return request({
    url: '/users/register',
    method: 'post',
    data
  })
}

export function login(data) {
  return request({
    url: '/users/login',
    method: 'post',
    data
  })
}

export function getUserInfo(id) {
  return request({
    url: `/users/${id}`,
    method: 'get'
  })
}

export function updateUserInfo(id, data) {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}
