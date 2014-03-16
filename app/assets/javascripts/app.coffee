
app = window.angular.module('app', [])

app.controller('AuctionController', ['$scope', '$http',
  ($scope, $http) ->
    startWS = ->
      wsUrl = jsRoutes.controllers.AppController.indexWS().webSocketURL()

      $scope.socket = new WebSocket(wsUrl)
      $scope.socket.onmessage = (msg) ->
        $scope.$apply( ->
          console.log "received : #{msg}"
          $scope.time = JSON.parse(msg.data).data
        )

    $scope.start = ->
      $http.get(jsRoutes.controllers.AppController.start().url).success( -> )

    $scope.stop = ->
      $http.get(jsRoutes.controllers.AppController.stop().url).success( -> )

    startWS()

    $scope.bidders = [
      {name: "Highley", id: 1, owes: 0, total: 0, payments: []},
      {name: "Heaton", id: 2, owes: 0, total: 0, payments: []},
      {name: "Jackson, L", id: 3, owes: 0, total: 0, payments: []},
      {name: "Andrews, S", id: 4, owes: 0, total: 0, payments: []},
      {name: "Andrews, H", id: 5, owes: 0, total: 0, payments: []},
      {name: "Moss", id: 6, owes: 0, total: 0, payments: []},
      {name: "Moyer", id: 7, owes: 0, total: 0, payments: []},
      {name: "Parker, K", id: 8, owes: 0, total: 0, payments: []}
    ]

    $scope.items = [
      {id: 101, category: "Item", donor: "Calloway's", description: "$25 Gift Card Basket", minbid: 5}
      {id: 102, category: "Item", donor: "Calloway's 2", description: "$25 Gift Card Basket 2", minbid: 5}
    ]
]).controller('ItemController', ['$scope',
  ($scope) ->
    $scope.addItem = ->
      $scope.items.push({id: parseInt($scope.idText), category: $scope.categoryCombo, donor: $scope.donorCombo, description: $scope.descriptionText, minbid: parseInt($scope.minbidText)})
      $scope.idText = ''
      $scope.categoryCombo = ''
      $scope.donorCombo = ''
      $scope.descriptionText = ''
      $scope.minbidText = ''
]).controller('BidderListController', ['$scope',
  ($scope) ->
    $scope.listBidders = ->
]).controller('BidderEditController', ['$scope',
  ($scope) ->
    $scope.editBidder = ->
      $scope.bidders.push({name: $scope.nameText, id: 0, owes: 0, total: 0, payments: []})
      $scope.nameText = ''
]).controller('BidderNewController', ['$scope',
  ($scope) ->
    $scope.addBidder = ->
      $scope.bidders.push({name: $scope.nameText, id: 0, owes: 0, total: 0, payments: []})
      $scope.nameText = ''
])
