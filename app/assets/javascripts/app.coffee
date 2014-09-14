
require [
  'angular',
  './services',
  './directives',
  './controllers',
  'angular-route'
],
  (angular) ->

    angular.module('AuctionHouse', ['AuctionHouse.services', 'AuctionHouse.directives', 'AuctionHouse.controllers', 'ngRoute'])
      .config(['$routeProvider',
        ($routeProvider) ->
          $routeProvider
            .when '/home',
              { templateUrl: 'templates/home.html',            controller: 'HomeCtrl' }
            .when '/bidders',
              { templateUrl: 'templates/bidders.html',         controller: 'BiddersCtrl' }
            .when '/items',
              { templateUrl: 'templates/items.html',           controller: 'ItemsCtrl' }
            .when '/bidentry',
              { templateUrl: 'templates/bidentry.html',        controller: 'BidEntryCtrl' }
            .when '/reconciliation',
              { templateUrl: 'templates/reconciliation.html',  controller: 'ReconCtrl' }
            .otherwise { redirectTo: '/home' }
      ])

    angular.bootstrap(document, ['AuctionHouse'])
