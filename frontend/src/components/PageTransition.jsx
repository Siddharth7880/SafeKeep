import { motion } from 'framer-motion';

const pageVariants = {
  initial: { opacity: 0, y: 15, scale: 0.99 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] } },
  exit: { opacity: 0, y: -10, transition: { duration: 0.25, ease: [0.22, 1, 0.36, 1] } }
};

export default function PageTransition({ children, className = '' }) {
  return (
    <motion.div
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
      className={className}
    >
      {children}
    </motion.div>
  );
}
