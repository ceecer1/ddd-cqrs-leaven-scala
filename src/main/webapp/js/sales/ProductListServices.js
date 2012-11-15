angular.module('productListServices', ['ngResource']).
    factory('Products', function($resource) {
        return $resource('products/', {}, {
            query: {method: 'GET', params: {containsText: 'abl'}, isArray: true}
        });
    });