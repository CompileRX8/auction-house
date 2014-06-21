
require [
  'angular',
  './controllers',
  './directives',
  './filters',
  './services',
  'angular-route'
],
  (angular, controllers) ->

    angular.module('myApp', ['myApp.filters', 'myApp.services', 'myApp.directives', 'ngRoute'])
      .config ['$routeProvider',
          ($routeProvider) ->
            $routeProvider
              .when '/view1',
                { templateUrl: 'partials/partial1.html', controller: controllers.MyCtrl1 }
              .when '/view2',
                { templateUrl: 'partials/partial2.html', controller: controllers.MyCtrl2 }
              .otherwise { redirectTo: '/view1' }
      ]

    angular.bootstrap document, ['myApp']
