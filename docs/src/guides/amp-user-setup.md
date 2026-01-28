# AMP Control API User Setup

This guide walks you through creating a dedicated user account in AMP for use with AutoStartStop's control API integration.

## Why create a dedicated user?

Creating a dedicated user for the plugin is recommended for several reasons:

- **Security and permission management**: This allows you to grant only the necessary permissions without exposing your main admin account
- **Session management**: Using your own user account can cause issues with features like "Remember Me" and sticky sessions, which may interfere with the plugin's authentication

## Step 1: Create a new user

1. Navigate to your **base ADS instance configuration**
2. Go to **User Management**
3. Click **Create User**
4. Enter a username (e.g., `instance_management_bot`)
5. Set a secure password and save it in a safe location
6. Keep the following options disabled:
    - **Password Expires**
    - **Require Password Change**

## Step 2: Configure permissions

You can either grant all permissions or configure specific permissions. For better security, we recommend using the minimum required permissions.

### Required permissions

The user needs permissions to manage the instances you want to control:

1. **All Instances** → **Local Instances** → **[Your ADS Instance]** → **Manage**
    - The "Local Instances" section name may vary depending on your setup
    - Typically, your ADS instance name is something like `ADS01`

2. **All Instances** → **Local Instances** → **[Target Instance]** → **Start, Stop, and Manage permissions**
    - Replace `[Target Instance]` with the specific instance you want AutoStartStop to manage

### Setting permissions

You can configure permissions in two ways:

- **Edit User Permissions**: Click on the user and select "Edit User Permissions"
- **Use a Role**: Create or assign a role with the required permissions

## Step 3: Configure AutoStartStop

Once you have created the user and configured permissions, add the credentials to your AutoStartStop configuration:

```{ .yaml }
defaults:
  server:
    control_api:
      type: 'amp'
      ads_url: 'http://localhost:8080/'
      username: 'instance_management_bot'  # The username you created
      password: 'your_password'            # The password you set
```

After completing this setup, your AutoStartStop plugin will be able to control your AMP instances using the dedicated user account.

For more details about these configuration options, see the [AMP Control API documentation](../control-api/amp.md).
