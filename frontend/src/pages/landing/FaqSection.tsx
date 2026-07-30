import React from "react";
import {
  Box,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from "@mui/material";
import { ExpandMore as ExpandMoreIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";

const FAQ_KEYS = ["q1", "q2", "q3", "q4", "q5", "q6"];

const FaqSection: React.FC = () => {
  const { t } = useTranslation();

  return (
    <Box component="section" sx={{ py: { xs: 3, md: 5 } }}>
      <Box sx={{ textAlign: "center", mb: 3 }}>
        <Typography
          className="retro-font-header"
          sx={{ color: "#ccc", fontSize: { xs: "1.2rem", md: "1.5rem" } }}
        >
          {t("landingPage.faq.title")}
        </Typography>
      </Box>
      <Box sx={{ maxWidth: 800, mx: "auto" }}>
        {FAQ_KEYS.map((key) => (
          <Accordion
            key={key}
            sx={{
              bgcolor: "#111",
              color: "#e2e8f0",
              border: "1px solid #333",
              "&:before": { display: "none" },
              "&.Mui-expanded": { borderColor: "#33ff00" },
            }}
          >
            <AccordionSummary
              expandIcon={<ExpandMoreIcon sx={{ color: "#33ff00" }} />}
            >
              <Typography sx={{ fontWeight: 600 }}>
                {t(`landingPage.faq.${key}.q`)}
              </Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Typography sx={{ color: "#94a3b8", lineHeight: 1.6 }}>
                {t(`landingPage.faq.${key}.a`)}
              </Typography>
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>
    </Box>
  );
};

export default FaqSection;
