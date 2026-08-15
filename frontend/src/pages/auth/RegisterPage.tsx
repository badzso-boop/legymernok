import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Alert,
  Link as MuiLink,
} from "@mui/material";
import apiClient from "../../api/client";
import { useAuth } from "../../context/AuthContext";

const registerSchema = z.object({
  fullName: z.string().min(3, { message: "fullNameMin" }),
  username: z.string().min(3, { message: "usernameMin" }),
  email: z.string().email({ message: "emailInvalid" }),
  password: z.string().min(6, { message: "passwordMin" }),
});

type RegisterFormInputs = z.infer<typeof registerSchema>;

const RegisterPage: React.FC = () => {
  const { login } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormInputs>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (data: RegisterFormInputs) => {
    setError(null);
    try {
      const response = await apiClient.post("/auth/register", data);

      login({
        token: response.data.token,
        username: response.data.username,
      });

      navigate("/");
    } catch (err: any) {
      if (err.response?.status === 409) {
        setError(t("errorUserExists"));
      } else {
        setError(t("errorRegister"));
      }
    }
  };

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: "60vh",
      }}
    >
      <Card
        sx={{
          minWidth: 350,
          maxWidth: 450,
          boxShadow: "0 8px 32px 0 rgba(0, 0, 0, 0.37)",
          backdropFilter: "blur( 4px )",
          border: "1px solid rgba(255, 255, 255, 0.18)",
          borderRadius: 2,
        }}
      >
        <CardContent sx={{ p: 4 }}>
          <Typography
            variant="h4"
            component="h1"
            gutterBottom
            textAlign="center"
            fontWeight="bold"
            color="primary"
          >
            {t("register")}
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box
            component="form"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
            sx={{ mt: 1 }}
          >
            <TextField
              margin="normal"
              required
              fullWidth
              id="fullName"
              label={t("fullName") || "Teljes név"}
              autoComplete="name"
              error={!!errors.fullName}
              helperText={
                errors.fullName?.message ? t(errors.fullName.message) : ""
              }
              {...register("fullName")}
            />

            <TextField
              margin="normal"
              required
              fullWidth
              id="username"
              label={t("username") || "Felhasználónév"}
              autoComplete="username"
              autoFocus
              error={!!errors.username}
              helperText={
                errors.username?.message ? t(errors.username.message) : ""
              }
              {...register("username")}
            />
            <TextField
              margin="normal"
              required
              fullWidth
              id="email"
              label={t("email") || "Email"}
              autoComplete="email"
              error={!!errors.email}
              helperText={errors.email?.message ? t(errors.email.message) : ""}
              {...register("email")}
            />
            <TextField
              margin="normal"
              required
              fullWidth
              label={t("password") || "Jelszó"}
              type="password"
              id="password"
              autoComplete="new-password"
              error={!!errors.password}
              helperText={
                errors.password?.message ? t(errors.password.message) : ""
              }
              {...register("password")}
            />

            <Box sx={{ display: "flex", justifyContent: "flex-end", mt: 3 }}>
              <Button
                type="submit"
                variant="contained"
                color="primary"
                disabled={isSubmitting}
                sx={{ px: 4, py: 1 }}
              >
                {t("register")}
              </Button>
            </Box>

            <Box sx={{ mt: 2, textAlign: "center" }}>
              <Typography variant="body2" color="text.secondary">
                {t("alreadyHaveAccount")}{" "}
                <MuiLink
                  component="button"
                  type="button"
                  onClick={() => navigate("/login")}
                >
                  {t("login")}
                </MuiLink>
              </Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default RegisterPage;
