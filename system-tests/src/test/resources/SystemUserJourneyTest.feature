Feature: Drone Delivery System
  As a system user
  I want to be able to register, login, create orders, and view them
  So that I can use the drone delivery service

  Scenario: Full user journey (Register -> Login -> Order -> View Orders -> Logout)
    Given the system starts from a clean state
    When a new user registers with valid username and password
    And the user logs in with the same credentials
    Then the system returns a session cookie for authentication

    When the user creates a new order with the following data:
      | Origin      | Destination | Weight |
      | Warehouse A | Client B    | 5.5    |
    Then the order creation request is accepted

    When the user requests the list of their orders
    Then the received orders list is not empty
    And the first order belongs to the user
    And the first order has "Client B" as destination

    When the user performs the final logout
    Then the disconnection is successful